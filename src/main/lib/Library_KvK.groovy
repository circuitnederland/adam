import org.cyclos.impl.system.ScriptHelper
import org.cyclos.entities.users.User
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.web.client.RestClient

class KvK {
    Binding binding
    ScriptHelper scriptHelper
    RestClient restClient
    Utils utils
    String url
    String apiKey


    KvK(Binding binding) {
		this.binding = binding
		def vars = binding.variables
        utils = new Utils(binding)
		scriptHelper = vars.scriptHelper as ScriptHelper
		restClient = vars.restClient as RestClient
        url = utils.techDetail('kvkAPIURL')
        apiKey = utils.techDetail('kvkAPIKey')
    }

   /**
    * Retrieves trade names from the KvK api using the KvK number from the given user.
    * See https://developers.kvk.nl/nl/documentation/basisprofiel-api.
    */
    String retrieveTradenames(User user) {
        def usr = scriptHelper.wrap(user)
        if (!usr.kvk) {
            return ''
        }
        // Call KvK api with usr.kvk.
        def kvkResult
        try{
            kvkResult = performRequest(usr.kvk)
        } catch(Exception e) {
            // Send mail to techteam and return the error message.
            def msg = "Exception during KvK api call to ${this.url}/${usr.kvk}: ${e.getMessage()}."
            utils.sendMailToTechTeam('Error KvK API', msg, true)
            return e.getMessage()
        }

        // No trade names found, return the statutaireNaam or naam (not sure if this is correct).
 		if (!kvkResult?.handelsnamen) {
			return kvkResult?.statutaireNaam ?: kvkResult?.naam
		}

		// Return the tradenames from the KvK result.
        return kvkResult?.handelsnamen?.collect { it.naam }.join(' | ')
    }

    /**
     * Performs the request for the given KvK number, returns a Map with the result
     * or an error message if the call gives an exception - this may be a 404 if the KvK nr does not exist.
     */
    Map<String, Object> performRequest(String kvkNr) {
        def result
            result = restClient.method(HttpMethod.GET)
			.uri("${this.url}/{kvkNr}", kvkNr)
			.header('apiKey', this.apiKey)
			.accept(MediaType.APPLICATION_JSON)
			.retrieve()
			.body(Map)
        return result
    }
}
