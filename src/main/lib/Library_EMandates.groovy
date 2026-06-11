import static groovy.transform.TypeCheckingMode.SKIP

import javax.servlet.ServletContext

import org.apache.commons.lang3.mutable.MutableInt
import org.cyclos.entities.system.CustomField
import org.cyclos.entities.system.CustomFieldPossibleValue
import org.cyclos.entities.system.CustomOperationField
import org.cyclos.entities.system.CustomWizardExecution
import org.cyclos.entities.system.CustomWizardField
import org.cyclos.entities.system.ExternalRedirectExecution
import org.cyclos.entities.users.QRecordCustomFieldPossibleValue
import org.cyclos.entities.users.RecordCustomFieldPossibleValue
import org.cyclos.entities.users.SystemRecord
import org.cyclos.entities.users.SystemRecordType
import org.cyclos.entities.users.User
import org.cyclos.entities.utils.DatePeriod
import org.cyclos.entities.utils.EntityBackedParameterStorage
import org.cyclos.impl.access.ConfigurationHandler
import org.cyclos.impl.contentmanagement.DataTranslationHandler
import org.cyclos.impl.InvocationContext
import org.cyclos.impl.system.BaseCustomFieldPossibleValueCategoryServiceLocal
import org.cyclos.impl.system.BaseCustomFieldPossibleValueServiceLocal
import org.cyclos.impl.system.ConfigurationAccessor
import org.cyclos.impl.system.CustomOperationFieldPossibleValueCategoryServiceLocal
import org.cyclos.impl.system.CustomOperationFieldPossibleValueServiceLocal
import org.cyclos.impl.system.CustomWizardExecutionStorage
import org.cyclos.impl.system.CustomWizardFieldPossibleValueCategoryServiceLocal
import org.cyclos.impl.system.CustomWizardFieldPossibleValueServiceLocal
import org.cyclos.impl.system.ScriptHelper
import org.cyclos.impl.users.RecordServiceLocal
import org.cyclos.impl.users.UserServiceLocal
import org.cyclos.impl.utils.conversion.ConversionHandler
import org.cyclos.impl.utils.LinkGeneratorHandler
import org.cyclos.impl.utils.formatting.FormatterImpl
import org.cyclos.impl.utils.persistence.EntityManagerHandler
import org.cyclos.model.ValidationException
import org.cyclos.model.system.fields.CustomFieldPossibleValueCategoryDTO
import org.cyclos.model.system.fields.CustomFieldPossibleValueCategoryVO
import org.cyclos.model.system.fields.CustomFieldPossibleValueDTO
import org.cyclos.model.system.fields.CustomFieldPossibleValueVO
import org.cyclos.model.system.fields.CustomFieldVO
import org.cyclos.model.system.fields.CustomFieldValueForSearchDTO
import org.cyclos.model.system.fields.LinkedEntityVO
import org.cyclos.model.users.records.RecordDataParams
import org.cyclos.model.users.records.SystemRecordQuery
import org.cyclos.model.users.recordtypes.RecordTypeVO
import org.cyclos.model.utils.DatePeriodDTO
import org.cyclos.model.utils.ResponseInfo
import org.cyclos.model.utils.TimeField
import org.cyclos.server.utils.DateHelper
import org.cyclos.server.utils.ObjectParameterStorage
import org.cyclos.utils.StringHelper
import org.springframework.http.HttpStatus

import com.fasterxml.jackson.databind.ObjectMapper

import groovy.transform.TypeChecked
import net.emandates.merchant.library.AmendmentRequest
import net.emandates.merchant.library.Configuration
import net.emandates.merchant.library.CoreCommunicator
import net.emandates.merchant.library.NewMandateRequest
import net.emandates.merchant.library.SequenceType
import net.emandates.merchant.library.StatusRequest
import net.emandates.merchant.library.StatusResponse
import net.emandates.merchant.library.DirectoryResponse.DebtorBank

@TypeChecked
class EMandates {
	static final String CONFIG_RESOURCE = "/emandates-config.xml"
	static final String CORE_COMM = "emandates.coreComm"
	
	Binding binding
	ObjectMapper objectMapper
	ScriptHelper scriptHelper
	ConfigurationHandler configurationHandler
	DataTranslationHandler dataTranslationHandler
	ConversionHandler conversionHandler
	FormatterImpl formatter
	ServletContext servletContext
	EntityManagerHandler entityManagerHandler
	CustomWizardFieldPossibleValueServiceLocal wizardPossibleValueService
	CustomWizardFieldPossibleValueCategoryServiceLocal wizardPossibleValueCategoryService
	CustomOperationFieldPossibleValueServiceLocal operationPossibleValueService
	CustomOperationFieldPossibleValueCategoryServiceLocal operationPossibleValueCategoryService
	LinkGeneratorHandler linkGeneratorHandler
	RecordServiceLocal recordService
	UserServiceLocal userService
	CoreCommunicator coreComm
	
	EMandates(Binding binding) {
		this.binding = binding
		def vars = binding.variables
		scriptHelper = vars.scriptHelper as ScriptHelper
		configurationHandler = vars.configurationHandler as ConfigurationHandler
		dataTranslationHandler = vars.dataTranslationHandler as DataTranslationHandler
		conversionHandler = vars.conversionHandler as ConversionHandler
		formatter = vars.formatter as FormatterImpl
		objectMapper = vars.objectMapper as ObjectMapper
		entityManagerHandler = vars.entityManagerHandler as EntityManagerHandler
		wizardPossibleValueService = vars.customWizardFieldPossibleValueService as CustomWizardFieldPossibleValueServiceLocal
		wizardPossibleValueCategoryService = vars.customWizardFieldPossibleValueCategoryService as CustomWizardFieldPossibleValueCategoryServiceLocal
		operationPossibleValueService = vars.customOperationFieldPossibleValueService as CustomOperationFieldPossibleValueServiceLocal
		operationPossibleValueCategoryService = vars.customOperationFieldPossibleValueCategoryService as CustomOperationFieldPossibleValueCategoryServiceLocal
		linkGeneratorHandler = vars.linkGeneratorHandler as LinkGeneratorHandler
		recordService = vars.recordService as RecordServiceLocal
		userService = vars.userService as UserServiceLocal
		
		servletContext = scriptHelper.bean(ServletContext)
		coreComm = servletContext.getAttribute(CORE_COMM) as CoreCommunicator
		if (!coreComm) {
			def configResource = this.class.getResourceAsStream(CONFIG_RESOURCE)
			if (!configResource) {
				throw new IllegalStateException("Couldn't find ${CONFIG_RESOURCE} file")
			}
			Configuration.defaultInstance().Load(configResource)
			
			coreComm = new CoreCommunicator()
			servletContext.setAttribute(CORE_COMM, coreComm)
		}
	}
	
	/**
	 * Mapping from the bank ID to custom field possible value internal name
	 */
	String bankIdToInternalName(String id) {
		id = StringHelper.trimToNull(id)
		if (id == null) {
			return null
		}
		def first = id.charAt(0)
		if (first ==~ /[a-z|A-Z]/) {
			return id
		} else {
			return "bank_${id}"
		}
	}
	
	/**
	 * Mapping from the custom field possible value internal name to bank ID
	 */
	String internalNameToBankId(String internalName) {
		internalName = StringHelper.trimToNull(internalName)
		if (internalName == null) {
			return null
		}
		return StringHelper.removeStart("bank_", internalName)
	}
	
	/**
	 * Updates the custom field possible values and categories 
	 */
	void updateDirectory() {
		// Call the API
		def dir = coreComm.directory()
		if (dir.isError) {
			throw new IllegalStateException("Error getting eMandates directory." + 
				"JSON response:\n" + objectMapper.writeValueAsString(dir?.errorResponse))
		}
		
		// Get the banks and countries
		def banks = dir.debtorBanks
		
		// Update the wizard custom fields
		def wizardFields = [
			'registration.debtorBank'
		].collect { entityManagerHandler.find(CustomWizardField, it) }
		.each { field ->
			updateDebtorBanks(field, banks, wizardPossibleValueService, wizardPossibleValueCategoryService)
		}
		
		// Update the operation custom fields		
		def operationFields = [
			'createEMandate.debtorBank', 
			'amendEMandate.debtorBank'
		].collect { entityManagerHandler.find(CustomOperationField, it) }
		.each { field ->
			updateDebtorBanks(field, banks, operationPossibleValueService, operationPossibleValueCategoryService)
		}
	}
	
	@TypeChecked(SKIP)
	private void updateDebtorBanks(CustomField field, List<DebtorBank> banks,
		BaseCustomFieldPossibleValueServiceLocal possibleValueService,
		BaseCustomFieldPossibleValueCategoryServiceLocal categoryService) {
		
		def countries = banks.collect { it.debtorBankCountry }
		def fieldVO = new CustomFieldVO(field.id)
		
		// First adjust the possible value categories, which are the countries
		def cats = field.possibleValueCategories
		countries.each { country ->
			if (!cats.find { cat -> cat.name == country }) {
				def cat = new CustomFieldPossibleValueCategoryDTO()
				cat.field = fieldVO
				cat.name = country
				categoryService.save(cat)
			}
		}
		
		// Remove all possible values whose banks don't exist
		def pvs = field.possibleValues
		def toRemove = pvs.findAll { pv ->
			def bankId = internalNameToBankId(pv.internalName)
			def existing = banks.find { bank -> bank.debtorBankId == bankId }
			return existing == null
		}
		toRemove.each { possibleValueService.remove(it) }
		pvs.removeAll(toRemove)
		
		// Finally make sure each bank exist as a possible value
		banks.each { bank ->
			def internalName = bankIdToInternalName(bank.debtorBankId)
			def cat = cats.find { cat -> cat.name == bank.debtorBankCountry }
			def catVO = new CustomFieldPossibleValueCategoryVO(cat.id)
			def existing = pvs.find { pv -> pv.internalName == internalName }
			if (existing) {
				// The possible value already exists. Make sure the value is updated
				existing.value = bank.debtorBankName
				existing.category = cat
			} else {
				// Doesn't exist yet. Create it
				def dto = new CustomFieldPossibleValueDTO()
				dto.field = fieldVO
				dto.category = catVO
				dto.internalName = internalName
				dto.value = bank.debtorBankName
				possibleValueService.save(dto)
			}
		}
	}
	
	/**
	 * Returns the bank name for the given id
	 */
	String bankName(String bankId) {
		def field = entityManagerHandler.find(
			CustomOperationField.class, 'createEMandate.debtorBank')
		def internalName = bankIdToInternalName(bankId) 
		return field.possibleValues.stream()
			.filter { it.internalName == internalName }
			.map { it.value }
			.findFirst()
			.orElse(null)
	}
	
	/**
	 * Request of a new mandate. Can be called either from the custom operation or registration wizard. 
	 * Returns the URL to which the user should be redirected.
	 */
	String newMandateRequest(User user, String entranceCode, String debtorReference,
		ObjectParameterStorage storage, String bankId) {
		// First create the record
		def data = recordService.getDataForNew(new RecordDataParams(
			recordType: new RecordTypeVO(internalName: 'eMandate')))
		def fields = scriptHelper.wrap(data.dto)
		fields.bankId = bankId
		fields.bankName = bankName(bankId)
		fields.owner = user
		def record = recordService.saveEntity(data.dto)

		// Store the record in the storage, so we can lookup it later
		storage.setObject('record', record)
		
		// Build the request parameters
		def req = new NewMandateRequest(
			entranceCode,
			'nl', // Language 
			null, // Use the default duration 
			record.id as String, // The eMandate id is the record id
			new Utils(binding).dynamicMessage('emDescription'), 
			debtorReference,
			bankId, // The debtor bank id
			record.id as String, // The purchase id is the record id
			SequenceType.RCUR, // We always request for recurring mandates 
			null // There is no maximum amount (only for B2B)
		)
		
		// Perform the request
		def resp = coreComm.newMandate(req)
		if (resp.isError) {
			throw new ValidationException(
				resp.errorResponse?.consumerMessage ?: resp.errorResponse?.errorMessage)
		}
		// Update the record
		fields = scriptHelper.wrap(record)
		fields.transactionId = resp.transactionID
		return resp.issuerAuthenticationURL
	}
	
	/**
	 * Request of a mandate amend. Returns the URL to which the user should be redirected.
	 */
	String amendMandateRequest(User user, ExternalRedirectExecution execution, String bankId) {
		// Find the current record. We need some data from it for the amend request.
		def curRecord = current(user)
		if (curRecord == null) {
			throw new ValidationException("No current eMandate")
		}
		def curFields = scriptHelper.wrap(curRecord)
		
		// Create a new record.
		def data = recordService.getDataForNew(new RecordDataParams(
			recordType: new RecordTypeVO(internalName: 'eMandate')))
		def newFields = scriptHelper.wrap(data.dto)
		newFields.bankId = bankId
		newFields.bankName = bankName(bankId)
		newFields.owner = user
		def newRecord = recordService.saveEntity(data.dto)

		// Build the request parameters
		def req = new AmendmentRequest(
			"operation${execution.id}",
			'nl', // Language
			null, // Use the default duration
			newRecord.id as String, // The eMandate id is the new record id
			new Utils(binding).dynamicMessage('emDescription'), 
			user.username as String, // The debtor reference is the username
			bankId, // The new debtor bank id
			newRecord.id as String, // The purchase id is the new record id
			SequenceType.RCUR, // We always request for recurring mandates
			curFields.iban as String, // Original iban
			curFields.bankId as String // Original bank id
		)
		
		// Perform the request
		def resp = coreComm.amend(req)
		if (resp.isError) {
			println(new ObjectMapper().writeValueAsString(resp))
			throw new ValidationException(
				resp.errorResponse?.consumerMessage ?: resp.errorResponse?.errorMessage)
		}
		
		// Store the new record in the storage, so we can lookup it later.
		// The status request later on will then fill in the status and rawMessage details.
		def storage = new EntityBackedParameterStorage(objectMapper, execution)
		storage.setObject('record', newRecord)

		// Update the new record.
		newFields = scriptHelper.wrap(newRecord)
		newFields.transactionId = resp.transactionID
		return resp.issuerAuthenticationURL
	}
	
	/**
	 * This is a generic callback. It is modeled like this because the eMandates
	 * java library uses a fixed merchant return URL.
	 * This will redirect the user to the actual custom operation callback URL
	 */
	ResponseInfo genericCallback(String transactionId, String entranceCode) {
		String url
		if (entranceCode.startsWith("operation")) { 
			def id = Long.valueOf(StringHelper.removeStart(entranceCode, "operation"))
			def execution = entityManagerHandler.find(ExternalRedirectExecution, id)			
			url = linkGeneratorHandler.customOperationExternalRedirect(execution)
		} else if (entranceCode.startsWith("wizard")) {
			def id = Long.valueOf(StringHelper.removeStart(entranceCode, "wizard"))
			def execution = entityManagerHandler.find(CustomWizardExecution, id)
			def executionStorage = conversionHandler.convert(CustomWizardExecutionStorage, execution.storage)
			url = linkGeneratorHandler.customWizardExternalRedirect(execution, executionStorage)
		} else {
			throw new IllegalStateException("Unhandled entranceCode: ${entranceCode}")
		}
		url += (url.contains('?') ? '&' : '?') + "transactionId=${transactionId}"
		def response = new ResponseInfo(HttpStatus.SEE_OTHER.value(), "")
		response.setHeader("Location", url)
		return response
	}
	
	/**
	 * This is the callback by the proper custom operation.
	 * It will check the status of the transaction
	 */
	Map<String, Object> callback(ObjectParameterStorage storage, String transactionId) {
		def record = storage.getObject('record') as SystemRecord
		def fields = scriptHelper.wrap(record)
		if (fields.transactionId != transactionId) {
			throw new ValidationException("Invalid transactionId")
		}
		
		// Now we need to check the status and update the record with this information.
		return updateStatus(record)
	}

	/**
	 * Perform the status request and return the response.
	 */
	StatusResponse retrieveStatus(String transactionId) {
		StatusRequest req = new StatusRequest(transactionId)
		StatusResponse resp = coreComm.getStatus(req)
		if (resp.isError) {
			throw new ValidationException(
				resp.errorResponse?.consumerMessage ?: resp.errorResponse?.errorMessage)
		}
		return resp
	}
	
	/**
	 * Calls the web service to update the eMandate status for the given record
	 */
	Map<String, Object> updateStatus(SystemRecord record) {
		def fields = scriptHelper.wrap(record)
		
		// Only proceed if the status is either open or pending
		def currentStatus = (fields.status as CustomFieldPossibleValue).internalName
		if (!['open', 'pending'].contains(currentStatus)) {
			return fields
		}
		
		// Retrieve the emandate status
		def transactionId = fields.transactionId as String
		def resp = retrieveStatus(transactionId)
		
		// Update the record fields
		fields.status = resp.status.toLowerCase()
		def acceptance = resp.acceptanceReport
		if (acceptance?.acceptedResult) {
			fields.iban = acceptance.debtorIBAN
			fields.accountName = acceptance.debtorAccountName
			fields.signerName = acceptance.debtorSignerName
			fields.validationReference = acceptance.validationReference
		}
		if (resp.statusDateTimestamp) {
			fields.statusDate = resp.statusDateTimestamp.toGregorianCalendar().time
		}
		fields.rawMessage = resp.rawMessage

		// Update the user profile.
		updateUserIBAN(fields)

		return fields
	}

	/**
	 * Store the IBAN from the eMandate in the user profile.
	 * Note: in case we come here in the context of the registration wizard, there is no user yet and we simply don't do anything.
	 */
	void updateUserIBAN(Map fields) {
		String status = (fields.status as CustomFieldPossibleValue).internalName
		if ('success' != status || fields.owner == null || !fields.owner instanceof User) {
			// The eMandate does not have a success status, or the owner is not known yet or not a user object, don't try to update the IBAN and just return.
			return
		}

		def usrDTO = userService.load((fields.owner as User).id)
		def usr = scriptHelper.wrap(usrDTO)
		def orgIban = usr.iban
		Utils utils = new Utils(binding)
		if (utils.isIbansEqual(orgIban as String, fields.iban as String)) {
			// The IBAN in the eMandate record is the same as the IBAN we already had for this user, there is nothing more to do.
			return
		}

		// The IBAN in the eMandate is different than the IBAN we have in the user profile.

		// First, inform our financial admin by mail.
		def vars = ['username': usr.username, 'iban_emandate': fields.iban, 'org_iban': orgIban]
		utils.sendMailToAdmin("Incassomachtiging van afwijkend iban", utils.dynamicMessage("emDifferentIBAN", vars), true)

		// Next, try to update the IBAN in the user profile.
		try{
			usr.iban = fields.iban
			userService.save(usrDTO)
		} catch (ValidationException vE) {
			// We could not update the user profile, maybe the IBAN from equens already exists on another user in our system,
			// or the user has an invalid profile (missing required field for example). Put the original IBAN back and inform the finadmin.
			// Don't use the userService to change the user profile field, because this may fail due to the validation problem.
			usr = scriptHelper.wrap((fields.owner as User))
			usr.iban = orgIban
			vars = ['username': usr.username, 'error': vE.validation?.firstError]
			utils.sendMailToAdmin("Fout tijdens afgifte incassomachtiging", utils.dynamicMessage("emErrorSaveIBAN", vars), true)
		}
	}

	/**
	 * Returns the current eMandate record for the given user
	 */
	SystemRecord current(User user) {
		def query = new SystemRecordQuery()
		query.type = new RecordTypeVO(internalName: 'eMandate')
		query.customValues = [
			new CustomFieldValueForSearchDTO(
				field: new CustomFieldVO(internalName: 'owner'),
				linkedEntityValues: [
					new LinkedEntityVO(user.id)
				] as Set
			)
		] as Set
		query.setPageSize(1) // Since the result is by default ordered by creation date, this gives us the newest record.
		def results = recordService.search(query).pageItems
		return results.isEmpty() ? null : entityManagerHandler.find(SystemRecord, results[0].id)
	}
	
	/**
	 * Checks the pending eMandates to determine whether the status has changed
	 */
	String checkPending() {
		def query = new SystemRecordQuery()
		query.type = new RecordTypeVO(internalName: 'eMandate')
		query.customValues = [
			new CustomFieldValueForSearchDTO(
				field: new CustomFieldVO(internalName: 'eMandate.status'),
				enumeratedValues: [
					new CustomFieldPossibleValueVO(internalName: 'pending')
				]
			)
		] as Set
		query.setSkipTotalCount(true)
		query.setUnlimited()
		def records = recordService.search(query).pageItems
		def cache = InvocationContext.newCacheFlusher()
		def stillPending = new MutableInt()
		def nowSuccess = new MutableInt()
		def nowExpired = new MutableInt()
		records.each {
			def record = entityManagerHandler.find(SystemRecord.class, it.id)
			def toIncrement = stillPending
			try {
				def fields = updateStatus(record)
				String newStatus = (fields.status as CustomFieldPossibleValue).internalName
				if (newStatus == 'success') {
					toIncrement = nowSuccess
				} else if (newStatus == 'expired') { 
					toIncrement = nowExpired
				}
			} catch (ValidationException e) {
				// Ignore and move forward
				println "Error: ${e.message}"
			}
			toIncrement.increment()
			cache.flush()
		}
		return "A total of ${records.size()} eMandates were pending, of which " + 
			"${nowSuccess} are now successful, ${nowExpired} are now expired " +
			" and ${stillPending} are still pending"
	}

	/**
	 * Checks open eMandates and updates their status. This will be called by a scheduled task running four times a day.
	 */
	String checkOpen() {
		def maxNrOfDays = 14
		def expirationPeriodInMinutes = 30
		def lastAttemptInMinutes = 10
		Date now = new Date()
		Date twoWeeksAgo = DateHelper.subtract(now, TimeField.DAYS, maxNrOfDays)
		Date expirationPeriodAgo = DateHelper.subtract(now, TimeField.MINUTES, expirationPeriodInMinutes)
		Date minutesAgo = DateHelper.subtract(now, TimeField.MINUTES, lastAttemptInMinutes)

		def query = new SystemRecordQuery()
		query.type = new RecordTypeVO(internalName: 'eMandate')
		query.customValues = [
			new CustomFieldValueForSearchDTO(
				field: new CustomFieldVO(internalName: 'eMandate.status'),
				enumeratedValues: [
					new CustomFieldPossibleValueVO(internalName: 'open')
				]
			)
		] as Set
		query.datePeriod = conversionHandler.convert(DatePeriodDTO, new DatePeriod(twoWeeksAgo, expirationPeriodAgo))
		query.setSkipTotalCount(true)
		query.setUnlimited()
		def records = recordService.search(query).pageItems
		// If there are no records, stop.
		if (records.isEmpty()) {
			return "No recent open eMandates found."
		}
		def msg = "There were ${records.size()} open eMandates newer than ${maxNrOfDays} days.\n"
		for (item: records) {
			def record = entityManagerHandler.find(SystemRecord.class, item.id)
			def fields = scriptHelper.wrap(record)
			msg += "Checking record created at ${formatter.format(record.creationDate)} for user ${(fields.owner as User)?.username} with statusDate '${formatter.format(fields.statusDate)}'.\n"
			// Skip records for which we already got a status some minutes ago.
			if (fields.statusDate != null && (fields.statusDate as Date).after(minutesAgo)) {
				msg += "Skipping, because statusDate is too recent: ${formatter.format(fields.statusDate)}.\n"
				continue
			}
			// Do a new status request for this record.
			try {
				fields = updateStatus(record)
				msg += "After new status request the status is ${(fields.status as CustomFieldPossibleValue).internalName}.\n"
			} catch(ValidationException vE) {
				msg += "Validation exception: ${vE.message}\n"
			}
		}
		// Send an email to techteam.
		new Utils(binding).sendMailToTechTeam("Open eMandates", msg, true)
		return msg
	}

	/**
	 * Builds up an HTML string containing relevant details from the given emandate record.
	 */
	String emandateHtml(SystemRecord record, User user) {
		if ( ! record ) {
			return ''
		}
		def fields = scriptHelper.wrap(record)
		def config = configurationHandler.getAccessor(user)
		def html = ''

		def bankName = fields.bankName
		def bankNameField = record.type.fields.find { it.internalName == 'bankName' }
		def bankNameLabel = dataTranslationHandler.getName(config.language, bankNameField)
		html += "<div><strong>${bankNameLabel}:</strong> ${bankName}</div>"

		def iban = fields.iban
		if (iban) {		
			def ibanField = record.type.fields.find { it.internalName == 'iban' }
			def ibanLabel = dataTranslationHandler.getName(config.language, ibanField)
			html += "<div><strong>${ibanLabel}:</strong> ${iban}</div>"
		}

		def accountName = fields.accountName
		if (accountName) {
			def accountNameField = record.type.fields.find { it.internalName == 'accountName' }
			def accountNameLabel = dataTranslationHandler.getName(config.language, accountNameField)
			html += "<div><strong>${accountNameLabel}:</strong> ${accountName}</div>"
		}

		def signerName = fields.signerName
		if (signerName) {
			def signerNameField = record.type.fields.find { it.internalName == 'signerName' }
			def signerNameLabel = dataTranslationHandler.getName(config.language, signerNameField)
			html += "<div><strong>${signerNameLabel}:</strong> ${signerName}</div>"
		}

		def statusField = record.type.fields.find { it.internalName == 'status' }
		def statusLabel = dataTranslationHandler.getName(config.language, statusField)
		RecordCustomFieldPossibleValue statusValue = fields.status as RecordCustomFieldPossibleValue
		def pv = QRecordCustomFieldPossibleValue.recordCustomFieldPossibleValue
		def statusValueLabel = dataTranslationHandler.getValue(config.language, statusValue, pv.value)
		html += "<div><strong>${statusLabel}:</strong> ${statusValueLabel}</div>"
		
		def statusDate = fields.statusDate
		if (statusDate) {
			def statusDateField = record.type.fields.find { it.internalName == 'statusDate' }
			def statusDateLabel = dataTranslationHandler.getName(config.language, statusDateField)
			html += "<div><strong>${statusDateLabel}:</strong> ${formatter.format(statusDate)}</div>"
		}
		return html
	}
}
