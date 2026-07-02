import org.cyclos.entities.system.CustomFieldPossibleValue
import org.cyclos.entities.system.ExternalRedirectExecution
import org.cyclos.model.utils.RequestInfo

import com.fasterxml.jackson.databind.ObjectMapper

Map<String, String> scriptParameters = binding.scriptParameters
ExternalRedirectExecution execution = binding.execution
RequestInfo request = binding.request
def transactionId = request.getParameter('transactionId')

def fields = new EMandates(binding).callback(execution, transactionId)

// Inform the user about the result and return to the buy credits main operation screen (which only works in the app, on main the user returns to home).
String status = (fields.status as CustomFieldPossibleValue).internalName
return [
    notification: new Utils(binding).dynamicMessage("emResult${status.capitalize()}"),
    backTo: scriptParameters.parentOperation,
    reRun: true
]
