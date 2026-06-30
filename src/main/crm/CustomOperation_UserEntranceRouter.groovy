import org.cyclos.entities.system.CustomOperation
import org.cyclos.entities.users.User
import org.cyclos.impl.system.ScriptHelper

Utils utils = new Utils(binding)
CRM crm = new CRM(binding)
ScriptHelper scriptHelper = binding.scriptHelper
User user = binding.user
Boolean showOpenAccount = false
Boolean showManualTransfer = false
Boolean showEMandate = false
String html

def idCheck = crm.getIDCheck(user)
if (idCheck) {
    // The identity of the user has been checked. We can let them enter.
    showOpenAccount = true
    html = utils.dynamicMessage("entrIDCheckedMsg")
} else {
    // The identity of the user is not checked yet. See what they have done so far.
    // Check if there is an emandate record for this user.
    EMandates emandates = new EMandates(binding)
    def record = emandates.newest(user)
    if (record) {
        def fields = scriptHelper.wrap(record)
        // If the redirect during the emandate creation failed (indicated by an empty statusDate field), try to get the status now.
        if(fields?.status?.internalName == 'open' && fields?.statusDate == null) {
            fields = emandates.updateStatus(record)
        }
        // The emandate status determines the msg to show and whether the emandate button should be shown.
        def status = fields?.status?.internalName ?: 'none'
        switch(status) {
            case 'cancelled':
            case 'expired':
            case 'failure':
            // Previous emandate was not succesful. Allow emandate to try again.
                showEMandate = true
                html = utils.dynamicMessage("entrStartMsg")
                def vars = ['status': emandates.retrieveTranslatedEMandateStatus(fields.status)]
                html += "<div>${utils.dynamicMessage('entrPrevEMFailedMsg', vars)}</div>"
                break
            case 'open':
            case 'pending':
            case 'none':
            // Previous emandate is still open/pending. Show waiting msg and don't allow new attempt.
                def vars = ['status': emandates.retrieveTranslatedEMandateStatus(fields.status)]
                html = utils.dynamicMessage("entrEMWaitingMsg", vars)
                break
            case 'success':
            // Previous emandate is s6, but no automated id_check. Show check msg and don't allow new attempt.
                def vars = ['em_name': fields.accountName, 'user_name': user.name]
                html = utils.dynamicMessage("entrEMPendingNameCheckMsg", vars)
                break
        }
    } else {
        // No emandate record. Show the standard start message and allow emandate.
        showEMandate = true
        html = utils.dynamicMessage("entrStartMsg")
    }
    // Always allow manual transfer, even if user has issued an emandate. So admin can ask them to identify by transfer from another iban.
    showManualTransfer = true
}
def actions = [
        openAccount: [
            parameters: [
                user: user.id,
                idCheck: idCheck
            ],
            enabled: showOpenAccount
        ],
        manualTransferEntrance: [
            enabled: showManualTransfer
        ],
        createEMandateEntrance: [
            parameters: [
                user: user.id
            ],
            enabled: showEMandate
        ]
    ]
// Only use actions that actually exist as internal actions on the router.
CustomOperation operation = binding.customOperation
def actualActions = actions.findAll { key, action -> 
    operation.actions.find {
        it.actionOperation.internalName == key
    }
}

return [
    content: html,
    actions: actualActions
]