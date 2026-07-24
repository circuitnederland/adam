import org.cyclos.entities.users.User
import org.cyclos.entities.users.UserRecord
import org.cyclos.impl.system.ScriptHelper
import org.cyclos.impl.users.UserServiceLocal
import org.cyclos.model.ValidationException
import org.cyclos.server.utils.SecureRandomHelper

/**
 * Extension point script to verifiy the bank account is legitimate.
 * Set the Extension point on Record type BankAcct for event Create 
 * (update is never allowed for admins on this record type).
 * Code block: 'Script code executed when the data is saved'.
 */

ScriptHelper scriptHelper = binding.scriptHelper
UserRecord record = binding.record
UserServiceLocal userService = binding.userService
def bankAcct = scriptHelper.wrap(record)
User user = record.user
Utils utils = new Utils(binding)
CRM crm = new CRM(binding)
def idCheck = crm.getIDCheck(user)

if (idCheck) {
    // The identity of the user has been verified before.
    // Is this bankAcct record being entered manually by a finadmin?
    if (bankAcct.eMandate) {
        // The bankAcct is created from an emandate, no checks needed.
        return
    }
    // The finadmin should enter a reference.
    if (!bankAcct.reference) {
        throw new ValidationException(utils.dynamicMessage("crmMissingRef"))
    }
    // The reference must be equal to the ref in the user profile, 
    // only a logged-in user could know.
    def usrDTO = userService.load(user.id)
    def usr = scriptHelper.wrap(usrDTO)
    if (bankAcct.reference != usr.reference) {
        throw new ValidationException(utils.dynamicMessage("crmWrongRef"))
    }
    // The reference is correct. Don't reuse refs, so generate a new one.
    usr.reference = SecureRandomHelper.randomNumeric(4)
    userService.save(usrDTO)
} else {
    // New candidate during entrance. Check the user identity by comparing 
    // bankAcct name with the user name.
    if (user.name == bankAcct.name) {
        // Names are equal, create a new automatic idCheck record.
        crm.createAutoIDCheck(user, record)
    } else {
        // Notify the finadmin, they should check the user identity manually.
        // If the bankAcct is created during emandate, email the finadmin.
        if (bankAcct.eMandate) {
            // @todo: send mail to finadamin to do manual ID check.
        }
        // @todo: if the finadmin is creating the bankAcct, show
        // an alert on the screen after they saved the record.
    }
}
