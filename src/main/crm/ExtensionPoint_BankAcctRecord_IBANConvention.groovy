import org.cyclos.entities.users.UserRecord
import org.cyclos.impl.users.RecordServiceLocal

/**
 * Extension point script to ensure the IBAN field complies to our conventions
 * (spaces and uppercase letters).
 * Set the Extension point on Record type BankAccount for event Create (update 
 * is never allowed for admins on this record type)
 * Code block:
 * 'Script code executed when the data is validated, but not yet saved'
 */

RecordServiceLocal recordService = binding.recordService
UserRecord record = binding.record
def bankAcct = scriptHelper.wrap(record)
def correctedIban = new Utils(binding).ibanByConvention(bankAcct.iban)

if (bankAcct.iban == correctedIban) {
    // The iban was already correct, so return and do nothing.
    return
}

// Update the corrected iban value.
bankAcct.iban = correctedIban

// Re-validate the corrected record, to ensure the iban field is still unique 
// after we applied the conventions.
def recordDTO = recordService.toDTO(record)
return recordService.validate(recordDTO)
