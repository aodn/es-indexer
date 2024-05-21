# What this folder for?

Our customized Geonetwork contains extra API call to get some internal data of dataset not expose by
standard API call.

Since we use standard Geonetwork image to do the testing, these API call will not be
there, we solve it by using as Mockito.spy() to intercept certain call and return 
the cannded data.

This folder is use to store values for API /aodn/records/{uuid}/info, the UUID will
be the name of the json file and return the content store inside the file.

For example Sample5.xml have the UUID 2852a776-cbfc-4bc8-a126-f3c036814892, so the
2852a776-cbfc-4bc8-a126-f3c036814892.json will be the content return if we call the
api above.