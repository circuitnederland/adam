import org.cyclos.entities.users.QRecordCustomFieldValue
import org.cyclos.entities.users.QUserRecord
import org.cyclos.entities.users.RecordCustomField
import org.cyclos.entities.users.User
import org.cyclos.entities.users.UserRecord
import org.cyclos.entities.users.UserRecordType
import org.cyclos.impl.system.ScriptHelper
import org.cyclos.impl.users.RecordServiceLocal
import org.cyclos.impl.utils.persistence.EntityManagerHandler
import org.cyclos.model.users.records.RecordDataParams
import org.cyclos.model.users.records.UserRecordDTO
import org.cyclos.model.users.records.RecordVO
import org.cyclos.model.users.records.UserRecordQuery
import org.cyclos.model.users.recordtypes.RecordTypeVO
import org.cyclos.model.users.users.UserLocatorVO
import org.cyclos.model.users.users.UserVO
import org.cyclos.utils.Page

import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.web.client.RestClient

class CRM {

    Binding binding
	ScriptHelper scriptHelper
    EntityManagerHandler entityManagerHandler
 	RecordServiceLocal recordService

    CRM(Binding binding) {
        this.binding = binding
        def vars = binding.variables
        scriptHelper = vars.scriptHelper as ScriptHelper
        entityManagerHandler = vars.entityManagerHandler as EntityManagerHandler
		recordService = vars.recordService as RecordServiceLocal
    }

    /**
     * Returns whether the given user is a company or not.
     */
    public boolean isCompany(User user) {
        return scriptHelper.wrap(user)?.containsKey('kvk')
    }

    /**
     * Retrieves the idCheck record for the given user.
     */
    public UserRecord getIDCheck(User user) {
		def query = new UserRecordQuery()
		query.type = new RecordTypeVO(internalName: 'idCheck')
        query.user = new UserVO(user.id)
		query.setPageSize(1)
		def results = this.recordService.search(query).pageItems
		return results.isEmpty() ? null : this.entityManagerHandler.find(UserRecord, results[0].id)
    }

    /**
     * Creates a new automatic idCheck record for the given user.
     * The bankAcct record is stored as the linked entity and the 
     * check field is set to the automatic check (identical or similar names).
     */
    public void createAutoIDCheck(User user, UserRecord bankAcct, String check) {
        RecordDataParams params = new RecordDataParams(
            user: new UserLocatorVO(id: user.id),
            recordType: new RecordTypeVO(internalName: 'idCheck')
        )
        UserRecordDTO idCheckDto = recordService.getDataForNew(params).dto
        def fields = scriptHelper.wrap(idCheckDto)
        fields.bankAcct = bankAcct
        fields.user_name = user.name
        fields.check = check
        recordService.save(idCheckDto)
    }

    /**
     * Strips the given tradename so we can use it for comparison:
     * - Change everything to lowercase.
     * - Remove spaces and dots.
     * - Remove common Dutch organisation terms: BV, NV, Stichting, ...
     */
    String stripTradename(String s) {
        return s
            .toLowerCase()
            .replaceAll(/[.\s]/, '')
            .replaceAll(/bv|nv|stichting|enzonen/, '')
    }

    /**
     * Checks the identity of the given user with their bank account record.
     * For companies, we compare the names with the tradenames from the KvK API.
     * Returns a string that may be used in the idCheck record (identical, similar) or null.
     */
    String checkNames(User user, Map<String, Object> bankAcct) {
        if (isCompany(user)) {
            def usr = scriptHelper.wrap(user)
            // If the tradeNames from the KvK are not received, return null.
            if (!usr.tradeNames || usr.tradeNames.contains('"fout"')) {
                return null
            }
            // Compare Cyclos name and bankAcct name with the KvK tradeNames.
            //def source = usr.tradeNames.split(' \\| ')
            def source = usr.tradeNames.tokenize('|')*.trim()
            def names = [user.name, bankAcct.name]
            if (source.containsAll(names)) {
                return 'identical'
            }
            // When not identical, the names may still by similar.
            def sourceStripped = source.collect{ stripTradename(it) }.toSet()
            def namesStripped = names.collect{ stripTradename(it) }.toSet()
            if (sourceStripped.containsAll(namesStripped)) {
                return 'similar'
            }
            // At least one of the names is not found as a tradename, return null.
            return null
        } else {
            // For consumers, only compare their name with the name in their bank account.
            return (user.name == bankAcct.name) ? 'identical' : null
        }
    }

    /**
    * Retrieves the bankAcct records for the given user.
    * Used in a Load Custom Field Value script on the bankAcct linked entity
    * in the idCheck user record or on the IBAN user profile field.
    */
    Page<RecordVO> getBankAcctRecords(User user) {
        def q = new UserRecordQuery()
        q.type = new RecordTypeVO(internalName: 'bankAcct')
        q.user = new UserVO(user.id)
        q.setUnlimited()
        return recordService.search(q)
    }

    /**
     * Retrieves the bankAcct record for the given user and the given iban.
     */
    UserRecord getBankAcctRecordForIban(User user, String iban) {
        // The iban in the bankAcct record adheres to our iban conventions,
        // so apply the conventions to the given iban as well before comparing.
        def safeIBAN = new Utils(binding).ibanByConvention(iban)
        def r = QUserRecord.userRecord
        def v = QRecordCustomFieldValue.recordCustomFieldValue
        def recordType = entityManagerHandler.find(UserRecordType, 'bankAcct')
        def field = entityManagerHandler.find(RecordCustomField, 'iban', recordType)
        UserRecord record = entityManagerHandler.from(r)
                .leftJoin(v).on(v.owner().eq(r), v.field().eq(field))
                .where(r.type().eq(recordType))
                .where(r.user.eq(user))
                .where(v.stringValue.eq(safeIBAN))
                .fetchFirst()
        return record
    }

    /**
     * Retrieves the bankAcct record for the given user
     * with the current iban profile field of this user.
     */
    UserRecord getActiveBankAcctRecord(User user) {
        def usr = scriptHelper.wrap(user)
        return getBankAcctRecordForIban(user, usr.iban)
    }

    /**
     * Retrieves the active emandate for the given user.
     * The active emandate is linked in the iban user record
     * with the current iban profile field.
     * Returns null if the user has no eMandate for their current IBAN.
     */
    UserRecord getActiveEmandate(User user) {
        def bankAcctRecord = getActiveBankAcctRecord(user)
        def fields = scriptHelper.wrap(bankAcctRecord)
        return fields?.eMandate
    }

    /**
     * Stores the given emandate in a bankAcct record.
     * Creates a new bankAcct record if there is none yet for this user.
     * If there is a bankAcct record already, links the emandate in it.
     */
    void storeEMandateInBankAcct(Map<String, Object> emFields) {
        User user = emFields.user
        def iban = emFields.iban
        // Check if there is a bankAcct record for this user with this iban.
        def bankAcctRecord = getBankAcctRecordForIban(user, iban)

        // If there is no bankAcct record yet, create one.
        if (!bankAcctRecord) {
            // Create a new bankAcct record.
            RecordDataParams params = new RecordDataParams(
                user: new UserLocatorVO(id: user.id),
                recordType: new RecordTypeVO(internalName: 'bankAcct')
            )
            UserRecordDTO record = recordService.getDataForNew(params).dto
            def fields = scriptHelper.wrap(record)
            fields.iban = emFields.iban
            fields.name = emFields.accountName
            fields.eMandate = emFields.id
            recordService.save(record)
        } else {
            // Store the emandate in the existing bankAcct record.
            def recordDTO = recordService.load(bankAcctRecord.id)
            def fields = scriptHelper.wrap(recordDTO)
            fields.eMandate = emFields.id
            recordService.save(recordDTO)
        }
    }
}

class KvK {
    Binding binding
    ScriptHelper scriptHelper
    RestClient restClient
    Utils utils
    String url
    String apiKey


    KvK(Binding binding) {
		this.binding = binding
		def vars = binding.variables
        utils = new Utils(binding)
		scriptHelper = vars.scriptHelper as ScriptHelper
		restClient = vars.restClient as RestClient
        url = utils.techDetail('kvkAPIURL')
        apiKey = utils.techDetail('kvkAPIKey')
    }

   /**
    * Retrieves trade names from the KvK api using the KvK number from the given user.
    * See https://developers.kvk.nl/nl/documentation/basisprofiel-api.
    */
    String retrieveTradenames(User user) {
        def usr = scriptHelper.wrap(user)
        if (!usr.kvk) {
            return ''
        }
        // Call KvK api with usr.kvk.
        def kvkResult
        try{
            kvkResult = performRequest(usr.kvk)
        } catch(Exception e) {
            // Send mail to techteam and return the error message.
            def msg = "Exception during KvK api call to ${this.url}/${usr.kvk}: ${e.getMessage()}."
            utils.sendMailToTechTeam('Error KvK API', msg, true)
            return e.getMessage()
        }

        // No trade names found, return the statutaireNaam or naam (not sure if this is correct).
 		if (!kvkResult?.handelsnamen) {
			return kvkResult?.statutaireNaam ?: kvkResult?.naam
		}

		// Return the tradenames from the KvK result.
        return kvkResult?.handelsnamen?.collect { it.naam }.join(' | ')
    }

    /**
     * Performs the request for the given KvK number, returns a Map with the result
     * or an error message if the call gives an exception - this may be a 404 if the KvK nr does not exist.
     */
    Map<String, Object> performRequest(String kvkNr) {
        def result
            result = restClient.method(HttpMethod.GET)
			.uri("${this.url}/{kvkNr}", kvkNr)
			.header('apiKey', this.apiKey)
			.accept(MediaType.APPLICATION_JSON)
			.retrieve()
			.body(Map)
        return result
    }
}
