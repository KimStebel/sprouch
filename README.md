Sprouch
=======

Sprouch is an asynchronous library for couchdb based on spray.

Features
--------

- Support for Scala 2.9 and 2.10
- Tested with CouchDB and BigCouch/Cloudant
- Connection via HTTP or HTTPS
- Authorization via HTTP Basic Auth 
- CRUD operations
- Javascript Views
- Attachments
- Bulk actions
- more features planned: see tickets

Documentation
-------------
- Tutorial: http://sprouch.blogspot.de/2012/12/sprouch-tutorial-basics.html
- Scaladocs: http://kimstebel.github.com/sprouch/scaladoc/2.9.2/#package

Sbt
---

### Scala 2.9 ###

```scala
resolvers += "sprouch repo" at "http://kimstebel.github.com/sprouch/repository"

libraryDependencies += "sprouch" % "sprouch_2.9.2" % "0.5.3"
```

### Scala 2.10 ###

```scala
resolvers += "sprouch repo" at "http://kimstebel.github.com/sprouch/repository"

libraryDependencies += "sprouch" % "sprouch_2.10.0-RC3" % "0.5.3"
```

Contribute!
-----------

Contributions are always welcome! Please drop me a line at kim.stebel@gmail.com if you want to help.
