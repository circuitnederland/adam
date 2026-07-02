import org.cyclos.entities.system.ExternalRedirectExecution
import org.cyclos.entities.users.User

ExternalRedirectExecution execution = binding.execution
Map<String, Object> formParameters = binding.formParameters
User user = formParameters.user
String bankId = formParameters.debtorBank.internalName

return new EMandates(binding).newMandateRequest(user, execution, bankId)
