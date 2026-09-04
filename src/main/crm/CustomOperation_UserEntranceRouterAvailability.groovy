import org.cyclos.entities.users.User
import org.cyclos.impl.system.ScriptHelper

ScriptHelper scriptHelper = binding.scriptHelper
CRM crm = new CRM(binding)
User user = binding.user

def usr = scriptHelper.wrap(user)
if (crm.isCompany(user)) {
    // Only companies that were explictly accepted may enter the network.
    return ('accepted' == usr.admission?.internalName)
} else {
    // Consumers may always enter the network.
    return true
}
