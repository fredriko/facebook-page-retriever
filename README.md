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
  * **Java 7** or later ([download](http://www.oracle.com/technetwork/java/javase/downloads/index.html))
  
  Optional dependencies, only required if you wish to build the program from source:
  
  * **maven 3** ([download](https://maven.apache.org/download.cgi))
  * **git** ([download](https://git-scm.com/downloads))

## Get the program

You can either [download a pre-compiled version of the program](bin/facebook-page-retriever.jar), or build it yourself.

Here's how to build the program from source. Open a terminal, and run the following commands (assuming you are in a unix-like environment):

 1. git clone https://github.com/fredriko/facebook-page-retriever.git
 2. cd facebook-page-retriever
 3. maven clean install

The last command will create a JAR file in which all the program's dependencies are bundled. The JAR is available in `target/facebook-page-retriever.jar`

## Set-up the program

In the following, it is assumed you type all commands in a terminal window, in the same directory as the `facebook-page-retriever.jar` is located. 
You only need to carry out the set-up step before you access Facebook Page data the first time. Type: 
 
 ```
 java -jar facebook-page-retriever.jar --setup --appId <application-id> --appSecret <application-secret>
 ```

Where `application-id` and `application-secret` are  application id and secret, respectively, you obtained when setting up your Facebook application. 
This will create a file containing the credentials required for accessing Facebook's API. Note that the generated file will
 contain your application secret: keep it, well, a secret.
 
 Optionally, you can have the configuration saved to an explicitly named file. Like so:
 
 ```
 java -jar facebook-page-retriever.jar --setup --appId <application-id> --appSecret <application-secret> --credentials <file>
 ```
 
 This is useful if you expect to use the program on several platforms and need to share its access credentials between platforms. 
 On the other hand, creating the access credentials this way requires you to include the `--credentials <file>` command 
 line options in every subsequent use of the program.
  
## Lists of useful Facebook Pages

In [this directory](pages/), there are a number of files containing lists of useful Facebook Pages, e.g., Swedish political 
parties, and news outlets. Please, feel free to add to the lists! Send me a pull request with any changes.

## Usage

To see the command line options available:

```
java -jar facebook-page-retriever.jar --help
```

For each Facebook Page supplied to the program, it produces two CSV files: one containing Posts from the page, and
another containing the Comments to the Posts. The headers of the CSV files are the same to facilitate processing in
downstream analysis software.

### Usage examples

Get the ten last posts from Reddit's Facebook page and write the results to CSV file in `/tmp`.

```
java -jar facebook-page-retriever.jar --fetch --pages https://www.facebook.com/reddit/ --maxPosts 10 --outputDirectory /tmp/
```


Process the ten last posts from Reddit's Facebook page and write only those containing the substring *AMA*, and their comments, to `/tmp`

```
java -jar facebook-page-retriever.jar --fetch --pages https://www.facebook.com/reddit/ --maxPosts 10 --terms AMA --outputDirectory /tmp
``` 


Get the January 2017 posts, and at most 10 comments per post, from Reddit's Facebook page, and write the results to `/tmp`

```
java -jar facebook-page-retriever.jar --fetch --pages https://www.facebook.com/reddit/ --maxComments 10 --since 17-01-01 --until 17-02-01 --outputDirectory /tmp
``` 

Get the last ten posts, and at most five comments from each post, from the pages of Swedish political parties (assuming the [list of pages](pages/swedish-political-parties.txt) 
is available in your current working directory):
 
```
java -jar facebook-page-retriever.jar --fetch --pages @/path/to/swedish-political-parties-txt -x 10 -y 5 -o /tmp
``` 
 
 

