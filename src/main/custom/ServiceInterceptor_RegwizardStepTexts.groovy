import org.cyclos.entities.users.UserGroup
import org.cyclos.impl.system.CustomWizardServiceLocal
import org.cyclos.impl.system.ServiceInterceptorContext
import org.cyclos.impl.utils.conversion.ConversionHandler
import org.cyclos.model.system.wizards.CustomWizardExecutionData
import org.cyclos.utils.StringHelper

/**
 * Service interceptor for CustomWizardService.
 * Operations: back and transition.
 * Shows a dynamic information text in a wizard step, depending on
 * the chosen Group. Uses the Utils library (dynamicMessage()).
 */

ServiceInterceptorContext context = binding.context
CustomWizardServiceLocal customWizardService = binding.customWizardService
ConversionHandler conversionHandler = binding.conversionHandler

CustomWizardExecutionData result = context.result

// Only intercept the registration wizard.
if (result.wizard?.internalName != 'registration') {
    return
}

// Skip Information texts that already have text.
if (result.step?.informationText) {
    // Empty Information texts that were marked as such.
    if (result.step?.informationText == 'empty') {
        result.step?.informationText = ''
    }
    return
}

// Find the custom text for this step, based on the step internal name and selected group.
// Note: the step.internalName is the qualified name, i.e. registration.email.
// The group.internalName by convention contains the member type (consumers or companies)
// i.e. nww_consumers_card or nww_companies_aspirant.
def execution = customWizardService.findExecution(result.key)
UserGroup group = customWizardService.getGroup(execution)
def memberType = StringHelper.split(group?.internalName, '_')[1]
def msgId = "${result.step?.internalName}.${memberType}"

// Convert this to camelCase, i.e. registrationEmailConsumers.
msgId = StringHelper.camelize(msgId)

// Use the helper method in the Utils library to determine the message.
result.step?.informationText = new Utils(binding).dynamicMessage(msgId)
