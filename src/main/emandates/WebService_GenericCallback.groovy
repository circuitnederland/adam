import org.cyclos.model.utils.RequestInfo

RequestInfo request = binding.request

def transactionId = request.getParameter('trxid')
def entranceCode = request.getParameter('ec')

return new EMandates(binding).genericCallback(transactionId, entranceCode)
