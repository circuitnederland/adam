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
     * Retrieve the idCheck record for the given user.
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
     * Create a new automatic idCheck record for the given user.
     * The bankAcct record is stored as the linked entity and the 
     * check field is set to automatic check (identical names).
     */
    public void createAutoIDCheck(User user, UserRecord bankAcct) {
        RecordDataParams params = new RecordDataParams(
            user: new UserLocatorVO(id: user.id),
            recordType: new RecordTypeVO(internalName: 'idCheck')
        )
        UserRecordDTO idCheckDto = recordService.getDataForNew(params).dto
        def fields = scriptHelper.wrap(idCheckDto)
        fields.bankAcct = bankAcct
        fields.user_name = user.name
        fields.check = 'identical'
        recordService.save(idCheckDto)
    }

    /**
    * Retrieve the bankAcct records for the given user.
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
     * Retrieve the bankAcct record for the given user and the given iban.
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
     * Retrieve the bankAcct record for the given user
     * with the current iban profile field of this user.
     */
    UserRecord getActiveBankAcctRecord(User user) {
        def usr = scriptHelper.wrap(user)
        return getBankAcctRecordForIban(user, usr.iban)
    }

    /**
     * Retrieve the active emandate for the given user.
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
     * Store the given emandate in a bankAcct record.
     * Create a new bankAcct record if there is none yet for this user.
     * If there is a bankAcct record already, link the emandate in it.
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