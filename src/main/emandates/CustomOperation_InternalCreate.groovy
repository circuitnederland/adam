import org.cyclos.entities.system.ExternalRedirectExecution
import org.cyclos.entities.users.User
import org.cyclos.model.NotificationException

ExternalRedirectExecution execution = binding.execution
Map<String, Object> formParameters = binding.formParameters
User user = formParameters.user
String bankId = formParameters.debtorBank.internalName

EMandates emandates = new EMandates(binding)

// If there is a previous emandate without statusDate, the user did not return correctly.
// Inform them to start over from the beginning - this will call updateStatus() still.
def previousEM = emandates.newest(user)
if (previousEM) {
    def fields = scriptHelper.wrap(previousEM)
    if (fields?.statusDate == null) {
        NotificationException.raiseError(new Utils(binding).dynamicMessage("emMissingStatusMsg"))
    }
}

return emandates.newMandateRequest(user, execution, bankId)
