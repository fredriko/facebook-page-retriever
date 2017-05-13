# Facebook Page Retriever

The Facebook Page Retriever is a command line utility for retrieving posts and comments from Facebook Pages and save them as CSV files.

You can either download and run a compiled version of the program, or build the program from the source yourself. The former
alternative is suitable for those of you who are just interested in getting Facebook Pages data, while the latter is
for you who'd like to have the latest version, or get your hands dirty with code.

Regardless of which route you take, there are a couple of things you need to get going.

## Pre-requisites

 * An application registered with Facebook. Don't worry, it's easier than it sounds. Follow
    the instructions [here](https://developers.facebook.com/docs/apps/register). 
    
    Select 'website' as platform.
     
    Use version 2.8 of the API (is it selectable?)
    
    Take note of the *application id*, and the *application secret* on the application's page: you'll need that information when setting up this program.

  * Java8
  * maven (optional: only required if you wish to build the program from source)
  * git (optional: only required if you wish to build the program from source)


## Get the program

You can either [download a pre-compiled version of the program](link), or build it yourself.

Here's how to build it from source:

 * open a terminal
 * mkdir facebook-page-retriever
 * cd facebook-page-retriever
 * git clone ...
 * maven clean install

The last command will create a JAR file in which all the program's dependencies are bundled. The JAR is available in target/....

## Set-up the program
 
 java -jar program.jar --setup ...
 
  this will create a file containing the credentials required for accessing Facebook's API, which is where the program gets the data
  
## Usage examples

## Lists of useful Facebook Pages

In this directory, there are a number of files containing useful Facebook Pages, e.g., Swedish news outlets, ...


