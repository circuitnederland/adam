import org.cyclos.entities.users.User
import org.cyclos.model.system.extensionpoints.UserExtensionPointEvent
import org.cyclos.impl.system.ScriptHelper

/**
 * User extension point script to call KvK API and store tradenames
 * on Create or Update event of a user (companies only).
 * 
 * Code block:
 * Script code executed when the data is validated, but not yet saved
 */

ScriptHelper scriptHelper = binding.scriptHelper
UserExtensionPointEvent event = binding.event
User user = binding.user
def usr = scriptHelper.wrap(user)

// Only continue if the kvk profile field is being mutated.
// In Create event this is always the case. In Update event, we must check.
if (event == UserExtensionPointEvent.UPDATE) {
    // Check if the kvk field of the current copy is different
    def curUsr = scriptHelper.wrap(currentCopy)
    if (curUsr?.kvk == usr.kvk) {
        // The kvk field is not being changed, so no need to call the KvK API.
        return
    }
}

usr.tradeNames = new KvK(binding).retrieveTradenames(user)
