If you would like to show profilefields of type multiselection in the WordPress Cyclos plugin, you need to take the following steps.

1. Create a Script of type 'Service interceptor'.

Name: User search interceptor
Run with all permissions : No
Scriptparameters: {paste the contents of service_interceptor_userfields.properties}
Script code executed after the service method: {paste the contents of service_interceptor_userfields.groovy}

2. Create a Service interceptor.

Script: User search interceptor
Services: UserService
Operations: search

3. Add permissions to the WP user Group or Product.

With the Service interceptor in place, you can use a multiselection profilefield as filter field, setting it as the 'Default filter for map directory' in the Cyclos configuration. Such a field will also be visible in the popup with the user data in the WordPress list or map.
If you would like to show a multiselection profilefield in the popup that is not set as the 'Default filter for map directory' in the Cyclos configuration, you need to enable 'Map filter' for this field on the 'Profile fields of other users' permission of the WordPress user (Group or Product).
