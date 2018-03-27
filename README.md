
# help-to-save-stride-frontend 

 [ ![Download](https://api.bintray.com/packages/hmrc/releases/help-to-save-stride-frontend/images/download.svg) ](https://bintray.com/hmrc/releases/help-to-save-stride-frontend/_latestVersion)

When running locally make sure that `stride-auth`, `stride-auth-frontend` and `stride-idp-stub` are
running. There is a known issue where the stub login process will not work if these apps are run
from the service manager, therefore for now it is necessary to run the apps from source on ports 9042, 9041 and 9043 respectively.

When testing with stride auth the stride stub will give you the option to pass in the roles you
want the stride session to have. In order to access the pages provided by help-to-save-stride-frontend
create some roles in `application.conf` under the key `stride.roles` and pass them to the stride
stub form.

### License

This code is open source software licensed under the [Apache 2.0 License]("http://www.apache.org/licenses/LICENSE-2.0.html").
