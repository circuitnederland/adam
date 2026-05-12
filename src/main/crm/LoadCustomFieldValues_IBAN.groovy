import org.cyclos.entities.users.User
import org.cyclos.entities.users.UserRecord
import org.cyclos.impl.system.ScriptHelper
import org.cyclos.impl.utils.persistence.EntityManagerHandler

/**
 * Load custom field values script for the IBAN user profile field.
 * Returns a list of IBAN values from the BankAcct record of the user.
 */

ScriptHelper scriptHelper = binding.scriptHelper
EntityManagerHandler entityManagerHandler = binding.entityManagerHandler
User user = binding.user

CRM crm = new CRM(binding)
def records = crm.getBankAcctRecords(user)

return records.collect { vo ->
    def record = entityManagerHandler.find(UserRecord.class, vo.id)
    def fields = scriptHelper.wrap(record)
    "${fields.iban}"
}
