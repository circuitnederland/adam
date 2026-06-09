import org.cyclos.entities.users.User
import org.cyclos.entities.users.UserRecord
import org.cyclos.impl.system.ScriptHelper

/**
 * Record extension point script to store additional information 
 * during the creation of an idCheck user record.
 * 
 * Code block:
 * Script code executed when the data is saved.
 */

ScriptHelper scriptHelper = binding.scriptHelper
UserRecord record = binding.record
User user = record.user

def idCheck = scriptHelper.wrap(record)

// Save the current name of the user in the record.
idCheck.user_name = user.name
