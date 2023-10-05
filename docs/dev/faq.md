# Development FAQs

## When do I have to rebuild the Docker image?

When you have changed ```build.sbt``` or any scala files in ```app```.

## How do I rebuild the Docker image?

To rebuild the Docker image, simply run ```sbt dockerize``` in your terminal. Make sure you are in the correct directory, which should be where you cloned the repo.

## What sbt command line options are there?

To see what commands are available, run ```sbt tasks```.