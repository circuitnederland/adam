import org.cyclos.entities.system.CustomFieldPossibleValue
import org.cyclos.entities.users.UserRecord

/**
 * Record extension point script to store emandate information 
 * in a bankAcct user record as soon as the status 
 * of an emandate user record changes into success.
 */

UserRecord emandate = binding.record
def em = scriptHelper.wrap(emandate)

// Only store the emandate if its status is success.
if ( (em.status as CustomFieldPossibleValue).internalName != 'success' ) {
    // The status is not success, so we don't need to do anything.
    return
}

new CRM(binding).storeEMandateInBankAcct(em)
