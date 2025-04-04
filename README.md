# Bell Choir

The following lab will play any given song. This uses multithreaded processing in order to assign notes to bells. The
following documentation will guide the user on how to run the program.

## Processing Description

## To Run the Application
This application can be run using Ant. To run, please follow these direction.

Open the terminal application of your choice (terminal on MacOS, command line on Windows, etc.)

Enter the following commands:

a. Navigate to the root folder where you'd like these files to go:

Example: `cd Desktop`

b. Clone the git repository to your computer:

`git clone https://github.com/abbyymaureen/OrangePlant.git`

c. To compile and create a JAR file:

`ant jar`

d. To run the jar file:

`ant run`

e. To run the jar file with arguments (specified file):

`ant run -Dsong=songs/mary.txt`

e. To clean up the terminal and remove build artifacts:

`ant clean`


## References
* ChatGPT - Found the error where notes would overlap, along with not taking in command line arguments.
* Nathan Williams -Tone files provided, helped to create an excellent boilerplate.