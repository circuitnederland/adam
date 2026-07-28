import org.cyclos.entities.users.User
import org.cyclos.impl.system.ScriptHelper

// Only show the entrance router to users that have not entered yet.
// When we assign the entrance Product individually, users keep it after entering.
ScriptHelper scriptHelper = binding.scriptHelper
User user = binding.user

def usr = scriptHelper.wrap(user)
return !usr.iban
