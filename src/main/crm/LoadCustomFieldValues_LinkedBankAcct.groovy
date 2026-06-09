import org.cyclos.entities.users.User
import org.cyclos.entities.users.UserRecord
import org.cyclos.impl.system.ScriptHelper
import org.cyclos.impl.utils.persistence.EntityManagerHandler

/**
 * Load custom field values script for the linked entity BankAcct field
 * in the idCheck user record.
 * Returns a list of BankAcct records of the user.
 */

ScriptHelper scriptHelper = binding.scriptHelper
EntityManagerHandler entityManagerHandler = binding.entityManagerHandler
User user = binding.record?.user

CRM crm = new CRM(binding)
return crm.getBankAcctRecords(user)
