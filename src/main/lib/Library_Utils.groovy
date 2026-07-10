import static groovy.transform.TypeCheckingMode.SKIP

import groovy.transform.TypeChecked
import jakarta.mail.internet.InternetAddress
import org.apache.commons.validator.routines.checkdigit.IBANCheckDigit
import org.cyclos.entities.users.SystemRecord
import org.cyclos.entities.users.SystemRecordType
import org.cyclos.impl.users.RecordServiceLocal
import org.cyclos.model.system.fields.CustomFieldPossibleValueVO
import org.cyclos.model.system.fields.CustomFieldValueForSearchDTO
import org.cyclos.model.system.fields.CustomFieldVO
import org.cyclos.model.users.records.SystemRecordQuery
import org.cyclos.model.users.recordtypes.RecordTypeVO
import org.cyclos.server.utils.LocaleHelper
import org.cyclos.server.utils.MessageProcessingHelper
import org.springframework.mail.javamail.MimeMessageHelper

/**
 * Utils library class containing several helper methods.
 */

@TypeChecked
class Utils {
    private Binding binding
    private Map<String,Map> recordData

    Utils(Binding binding) {
        this.binding = binding
        recordData = [:]
    }

    /**
     * Ensures the given IBAN complies with the pattern conventions for IBANs we use (spacing and uppercase).
     *
     * If the given IBAN already complies with these conventions, it is returned unchanged.
     * If not, the corrected IBAN is returned.
     * 
     * Note: we also allow non-Dutch IBANs, so the number of characters may vary. This is why we
     * can not use a Cyclos input mask for this.
     *
     * This method does NOT check if the given IBAN is a valid IBAN, this is done by the validation script.
     * This is because we can not correct invalid IBANs automatically, so we let the validation script throw a validation exception.
     */
    String ibanByConvention(String iban) {
        if( this.isIbanConventionCompliant(iban) ) {
            // The IBAN pattern is fine, return it as-is.
            return iban
        }
        return this.correctIbanPattern(iban)
    }

    /**
     * Corrects the pattern of a given IBAN by putting a space after each block of four characters and using uppercase letters.
     */
    String correctIbanPattern(String iban) {
        String correctedIban = ''
        int pos = 0
        iban.replaceAll("\\s",'').each {
            correctedIban += it
            pos ++
            if ( pos % 4 === 0 ) {
                correctedIban += ' '
            }
        }
        return correctedIban.toUpperCase()
    }
    
    /**
     * Returns whether the given IBAN complies to the conventions we use for IBANs:
     * - A space after each block of four characters.
     * - Only uppercase letters.
     *
     * Example: NL02 ABNA 0123 4567 89
     */
    Boolean isIbanConventionCompliant(String iban) {
        return iban ==~ /^([A-Z0-9]{4} )*[A-Z0-9]{1,4}$/
    }

    /**
     * Returns whether the given IBAN is a valid IBAN.
     */
    Boolean isIbanValid(String iban) {
        return IBANCheckDigit.IBAN_CHECK_DIGIT.isValid(iban.replaceAll("\\s", ""))
    }

    /**
     * Checks whether two given IBANs are the same, ignoring upper-/lowercase and spaces.
     */
    Boolean isIbansEqual(String ibanA, String ibanB){
        return ibanA?.replace(" ","").equalsIgnoreCase(ibanB?.replace(" ", ""))
    }

	/**
	 * Sends an e-mail to the admin with the given message and subject.
	 */
    void sendMailToAdmin(String subject, String msg, Boolean isOnCommit = false, Boolean isHtml = false) {
        msg = "${dynamicMessage('adminMailSalutation')}\n\n${msg}\n\n${dynamicMessage('adminMailClosing')}"
        sendMail("Admin United Economy", techDetail('mailAdmin'), subject, msg, isOnCommit, isHtml)
    }

    /**
     * Sends an e-mail to the tech team with the given message and subject.
     */
    void sendMailToTechTeam(String subject, String msg, Boolean isOnCommit = false) {
        sendMail("Tech Team United Economy", techDetail('mailTechTeam'), subject, msg, isOnCommit)
    }

    /**
     * Sends an e-mail to the requested addressee with the given message and subject.
     */
    @TypeChecked(SKIP)
    void sendMail(String toName, String toMail, String subject, String msg, Boolean isOnCommit = false, Boolean isHtml = false) {
        def fromEmail = binding.sessionData.configuration.smtpConfiguration.fromAddress
        String fromName = binding.sessionData.configuration.emailName
        def sender = binding.mailHandler.mailSender
        def message = sender.createMimeMessage()
        def helper = new MimeMessageHelper(message, true, "UTF-8")
        helper.to = new InternetAddress(toMail, toName)
        helper.from = new InternetAddress(fromEmail, fromName)
        helper.subject = subject
        if (isHtml) {
            msg = msg.replace("\r\n", "<br>").replace("\n", "<br>").replace("\r", "<br>")
            helper.setText(msg, true)
        } else {
            helper.setText msg
        }
        if (isOnCommit) {
            binding.scriptHelper.addOnCommit {
                sender.send message
            }
        } else {
            binding.scriptHelper.addOnRollback {
                sender.send message
            }
        }
    }

    /**
     * Returns the contents of the system record field with the given recordtype and code.
     * If the field does not exist, returns the scriptParameter with the given code or the code itself.
     * If an optional recordIdentifier arg is passed, search for the record with this identifier custom field.
     * This is used for multi recordtypes. If no recordIdentifier arg is passed, use the single form record type.
     */
    @TypeChecked(SKIP)
    private Object _getRecordData(String recordTypeInternalName, String code, String recordIdentifier = null) {
        // Determine the key for the recordData Map.
        String key = recordTypeInternalName + (recordIdentifier ? "_${recordIdentifier}" : '')
        if (!recordData[key]) {
            RecordServiceLocal recordService = binding.recordService as RecordServiceLocal
            if (recordIdentifier) {
                // Look up the record for list recordtype, using the recordtype internal name and the record identifier custom field.
                def query = new SystemRecordQuery()
                query.type = new RecordTypeVO(internalName: recordTypeInternalName)
                query.customValues = [
                    new CustomFieldValueForSearchDTO(
                        field: new CustomFieldVO(internalName: "${recordTypeInternalName}.identifier"),
                        enumeratedValues: [
                            new CustomFieldPossibleValueVO(internalName: recordIdentifier)
                        ]
                    )
                ] as Set
                query.setPageSize(1) // Since the result is by default ordered by creation date, this gives us the newest record.
                def results = recordService.search(query).pageItems
                def record = results.isEmpty() ? null : binding.entityManagerHandler.find(SystemRecord, results[0].id)
                recordData[key] = binding.scriptHelper.wrap(record)
            } else {
                // Look up the record for single form recordtype, using only the recordtype internalName.
                def recordType = binding.entityManagerHandler.find(SystemRecordType, recordTypeInternalName)
                def record = recordService.getSingleFormRecord(recordType)
                recordData[key] = binding.scriptHelper.wrap(record)
            }
        }
        // If the field exists, return its value. Use containsKey(), because a boolean field value might be Groovy-false.
        if ( recordData[key].containsKey(code) ) {
            return recordData[key][code]
        }
        // The field was not found, return either a scriptParameter with the same name, or the code itself.
        return binding.scriptParameters[code] ?: code
    }

    /**
     * Returns a text message with any placeholders replaced by the dynamic texts in the given vars Map.
     * The text message is taken from either the customTexts system record or the scriptParameters.
     * If neither exists, the code itself is returned.
     * No type check, because the binding variables are unknown.
     */
    @TypeChecked(SKIP)
    String dynamicMessage(String code, Map<String, Object> vars = null) {
        // Use the current active language of the user, if there is one.
        String lng = binding.sessionData?.loggedUser?.locale
        if (!lng) {
            // If there is none, use the default language of the active configuration, with fallback to Dutch.
            def defaultConfLng = binding.sessionData.configuration?.defaultLanguage?.template
            lng = defaultConfLng ? LocaleHelper.mapLocale(defaultConfLng) : 'nl'
        }
        String messageHolder = (String) _getRecordData('customTexts', code, lng)
        if (!vars) {
            return messageHolder
        }
        messageHolder = messageHolder.replace('\\n', '\\\n')
        return MessageProcessingHelper.processVariables(messageHolder, vars)
    }

    /**
     * Returns the technical detail with the given code, taken from the technical details system record or the scriptparameters.
     * If neither exists, the code itself is returned.
     */
    String techDetail(String code) {
        return (String) _getRecordData('techDetails', code)
    }

    /**
     * Returns the technical detail boolean with the given code, taken from the technical details system record or the scriptparameters.
     * If neither exists, the code itself is returned.
     */
    Boolean techDetailBoolean(String code) {
        return (Boolean) _getRecordData('techDetails', code)
    }
}
