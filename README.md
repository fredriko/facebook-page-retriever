# Facebook Page Retriever

The Facebook Page Retriever is a command line utility for retrieving posts and comments from Facebook Pages and save them as CSV files.

You can either download and run a compiled version of the program, or build the program from the source yourself. The former
alternative is suitable for those of you who are just interested in getting Facebook Pages data, while the latter is
for you who'd like to get your hands dirty with code.

Regardless of which route you take, there are a couple of things you need to get going.

## Pre-requisites

 * An **application registered with Facebook**. Follow the instructions [here](https://developers.facebook.com/docs/apps/register). 
    * I don't believe any of the existing application categories is spot on, so select **website** as platform in lack of a better option.
    * Take note of the **application id**, and the **application secret** on the application's page: you'll need that information when setting up this program.
  * **Java 7** or later [download and installation instructions](http://www.oracle.com/technetwork/java/javase/downloads/index.html)
  
  Optional dependencies, only required if you wish to build the program from source:
  
  * **maven 3** [download and installation instructions](https://maven.apache.org/download.cgi)
  * **git** [download and installation instructions](https://git-scm.com/downloads)

## Get the program

You can either [download a pre-compiled version of the program](bin/facebook-page-retriever.jar), or build it yourself.

Here's how to build the program from source. Open a terminal, and run the following commands (assuming you are in a unix-like environment):

 1. mkdir facebook-page-retriever
 2. cd facebook-page-retriever
 3. git clone https://github.com/fredriko/facebook-page-retriever.git
 4. maven clean install

The last command will create a JAR file in which all the program's dependencies are bundled. The JAR is available in `target/facebook-page-retriever.jar

## Set-up the program

In the following, it is assumed you type all commands in a terminal window, in the same directory as the `facebook-page-retriever.jar` is located. 
You only need to carry out the set-up step before you access Facebook Page data the first time. Type: 
 
 `java -jar facebook-page-retriever.jar --setup --appId <your-facebook-application-id> --appSecret <your-facebook-application-secret>`

Where `your-facebook-application-id` and `your-facebook-application-secret` are the ones you obtained when setting up your Facebook application. 
This will create a file containing the credentials required for accessing Facebook's API. Note that the generated file will
 contain your application secret: keep it, well, a secret.
  
## Lists of useful Facebook Pages

In [this directory](pages/), there are a number of files containing lists of useful Facebook Pages, e.g., Swedish political 
parties, news outlets, large airline carriers, etc. Please, feel free to add to the lists! Send me a pull request with any changes.

## Usage examples

To see the command line options available

`java -jar facebook-page-retriever.jar --help`



