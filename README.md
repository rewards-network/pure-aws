# Pure AWS
![Sonatype Nexus (Releases)](https://img.shields.io/nexus/r/com.rewardsnetwork/pure-aws-s3_2.13?label=latest&server=https%3A%2F%2Foss.sonatype.org)

A Scala integrations library for AWS using principles of pure functional programming.
Depends heavily on Cats, Cats Effect, and FS2.

Currently includes the following modules, with more to come:
* `pure-aws-s3`: S3 object sources and sinks
* `pure-aws-s3-testing`: Test helpers to ensure you're using the S3 clients correctly
* `pure-aws-sqs`: Basic and simplified SQS access
* `pure-aws-sqs-refined`: Builds on top of `pure-aws-sqs` with `refined` integration for type-safe method parameters.

## Setup
This library is published for both Scala 2.12 and 2.13.
Scala 3 support will be coming, but is dependent on upstream projects also supporting it.
```
libraryDependencies += "com.rewardsnetwork" %% "<module-name>" % "<latest tag>"
```

## API Docs
* [S3](https://javadoc.io/doc/com.rewardsnetwork/pure-aws-s3_2.13/latest/com/rewardsnetwork/pureaws/s3/index.html)
* [S3 Testing](https://javadoc.io/doc/com.rewardsnetwork/pure-aws-s3-testing_2.13/latest/com/rewardsnetwork/pureaws/s3/testing/index.html)
* [SQS](https://javadoc.io/doc/com.rewardsnetwork/pure-aws-sqs_2.13/latest/com/rewardsnetwork/pureaws/sqs/index.html)
* [SQS Refined](https://javadoc.io/doc/com.rewardsnetwork/pure-aws-sqs-refined_2.13/latest/com/rewardsnetwork/pureaws/sqs/refined/index.html)

## License
Copyright 2020 Rewards Network Establishment Services

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0
    
Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.

## Usage
The libraries in this repository follow a general architecture pattern you should be aware of.
Each library has a "Pure" client layer, followed by a "Simplified" client layer, and optionally some kind of "Refined" client layer above that (currently only for SQS).
Each one adds enhancements on top of the other, and the base libraries feature both the pure and simplified layers.

For example, here is how you create an S3 and SQS client:
```scala
import cats.effect.{Blocker, IO}
import com.rewardsnetwork.pureaws.s3.PureS3Client
import com.rewardsnetwork.pureaws.sqs.PureSqsClient
import software.amazon.awssdk.regions.Region

Blocker[IO].flatMap { blocker => //Everything needs a Blocker
  val region = Region.US_EAST_1

  val pureS3clientResource = PureS3Client.async[IO](blocker, region)
  val pureSQSclientResource = PureSqsClient.async[IO](blocker, region)
}
```

The "Pure" clients for each library have Sync/Async variants, based on the underlying AWS client.
Due to API differences, they only resolve to a common subset of available operations between them.
Generally you should prefer the Async clients as they are natively non-blocking, but consider the Sync clients if necessary for semantic or performance reasons.

Each library has a host of simplified clients you can use as well.
The functionality of these clients are optimized to meet specific business needs, and might not be all-encompassing.
If you find yourself using the pure client for any reason, consider thinking of a way it can be simplified and incorporate it into an existing simplified client or create a new specialized one.

These simplified clients are as follows:

### S3

#### SimpleS3Client
The main entrypoint for working with S3 should be the `SimpleS3Client`.
It contains all of the subsequent clients inside of it, for you to access as-needed.
For modularity and separation of concerns, we've separated out the client types based on use-case, but if you need access to everything at once, this is the client you want.

```scala
import com.rewardsnetwork.pureaws.SimpleS3Client

val s3ClientResource: Resource[IO, SimpleS3Client[IO]] = Blocker[IO].flatMap(SimpleS3Client.async[IO](_, region))

s3ClientResource.use { client =>
  ///Access each of the clients within here
}
```

Detailed below are each of these clients and their individual use-cases.

#### S3ObjectOps
Perform basic operations on S3 objects, available at `SimpleS3Client#ops` or by itself:

```scala
import com.rewardsnetwork.pureaws.S3ObjectOps

val opsResource: Resource[IO, S3ObjectOps[IO]] = Blocker[IO].flatMap(S3ObjectOps.async[IO](_, region))

opsResource.use { ops =>

  //Define copy or delete operations manually...
  val copy = ops.copy("oldBucket", "oldKey", "newBucket", "newKey")
  val delete = ops.delete("oldBucket", "oldKey")
  
  //Or use a simplified version as `move` which copies and deletes in sequence
  val move = ops.move("oldBucket", "oldKey", "newBucket", "newKey")

  move
} //IO[Unit]
```

#### Sink
Write S3 objects using S3 (multipart not currently supported), available at `SimpleS3Client#sink` or by itself:

```scala
import com.rewardsnetwork.pureaws.S3Sink

val sinkResource: Resource[IO, S3Sink[IO]] = Blocker[IO].flatMap(S3Sink.async[IO](_, region))

sinkResource.use { sink =>
  Stream("hello", "world", "and all who inhabit it")
    .through(fs2.text.encodeUtf8)
    .through(sink.writeText(bucket, path)) //Can also write raw bytes, and set custom content type
    .compile
    .drain
} //IO[Unit]
```

#### Source
Stream S3 objects from S3 as bytes, available at `SimpleS3Client#source` or by itself:

```scala
import com.rewardsnetwork.pureaws.S3Source

val sourceResource: Resource[IO, S3Source[IO]] = Blocker[IO].flatMap(S3Source.async[IO](_, region))

Stream.resource(sourceResource).flatMap { source =>
  //Stream bytes from an object
  val byteStream = source.readObject("myBucket", "myKey") //Stream[IO, Byte]
  
  //Get an object's metadata and a stream of bytes
  val metadataAndByteStream = source.readObjectWithMetadata("myBucket", "myKey") //IO[(Map[String, String], Stream[IO, Byte])]

  metadataAndByteStream.flatMap { case (metadata, stream) =>
    val metadataKeys = metadata.keys.toList.mkString(", ")
    val logMetadataKeys = IO(println(s"Metadata keys available: $metadataKeys"))

    val getLines = stream
      .through(fs2.text.decodeUtf8[IO])
      .through(fs2.text.lines[IO])
      .compile
      .toList

    logMetadataKeys >> getLines
  } //IO[List[String]]
}
```

### SQS
The preferred way to use `pureaws-sqs` is to pull in the Simple client with an Async backend.

```scala
import com.rewardsnetwork.pureaws.sqs.SimpleSqsClient

val client: Resource[IO, SimpleSqsClient[IO]] = SimpleSqsClient.async[IO](blocker, region)
client.use { c =>
  c.streamMessages("url-to-my-queue", maxMessages = 10).take(3).compile.drain.as(ExitCode.Success)
}
```

A `SimpleSqsClient` is a more type-safe alternative to dealing with the traditional client, as it creates requests in the background and parses their responses.

Using the `refined` library, we can validate our function parameters at compile-time instead of making the request ourselves and assuming it is correct.
To take advantage of this, import the `pureaws-sqs-refined` library.
`refined` support is currently limited to method parameters and some returned types.
To see all available refined types used by this library, see `package.scala` in the `pureaws-sqs-refined` module.

When using SQS, be sure to delete messages after you are done processing.
To make this easier on yourself, use the `autoDelete` method on each SQS message.
This method takes an `F[Boolean]` predicate as an argument, and if it returns true, it will delete the message, otherwise do nothing.

## Testing
Some libraries may have testing modules implemented separately as-needed.
Presently there is just `pureaws-s3-testing`.

### S3 Testing
There is a small backend for testing the simplified clients, such as `S3Sink` and `S3Source`, available as `S3TestingBackend.inMemory`.
It is designed to be used with the `Test` clients, and they will throw exceptions for certain common operations such as when you try to get an object that does not exist.

All components are available in the `testing` package in `pureaws-s3-testing`.

```scala
import com.rewardsnetwork.pureaws.s3.testing._
import com.rewardsnetwork.pureaws.s3._
import fs2.Stream
import fs2.text._

//First, make a backend
val program = S3TestingBackend.inMemory[IO].flatMap { backend =>
  //Plug the backend into each component you test
  val sink: S3Sink[IO] = TestS3Sink(backend)

  //Components that share a backend will be testable together as one system
  val source: S3Source[IO] = TestS3Source(backend)

  val exampleText = "Hello world!"

  val writeText = Stream(exampleText).through(fs2.text.encodeUtf8).through(sink.writeText("bucket", "key"))

  val readText = source
    .readObject("bucket", "key")
    .through(fs2.text.decodeUtf8)
    .stdOutLines //prints out the object contents

  (writeText >> readText).compile.drain
}

program.unsafeRunSync() //Hello world!
```
