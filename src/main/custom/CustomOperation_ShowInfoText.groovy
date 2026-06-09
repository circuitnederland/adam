import org.cyclos.entities.system.CustomOperation
import org.cyclos.entities.users.User
import org.cyclos.impl.users.ProfileFieldHandler
import org.cyclos.utils.StringHelper

/**
 * Custom operation script that shows the Information text of the Custom operation.
 * Most variables available in the wysiwyg are replaced by actual user runtime data.
 * Use in Custom operations that should just show information, without form/submit.
 * Set Result type to 'Rich text' and 'Show form' to 'Only if there are missing 
 * required paramters'. This way, the form is never shown because there are no 
 * form fields. The result of the operation script is just to return the 
 * contents of the Information text.
 * Note: not <all> variables seem to get replaced, for example {group} does not, 
 * while {groupSet} does work.
 */

ProfileFieldHandler profileFieldHandler = binding.profileFieldHandler
CustomOperation customOperation = binding.customOperation
// Note: the user may not be in the binding directly, if the scope of the Custom operation is intenal.
User user = sessionData.loggedBasicUser

def vars = profileFieldHandler.getUserVariablesFunction(user)
return StringHelper.replaceVariables(customOperation.informationText, vars)
