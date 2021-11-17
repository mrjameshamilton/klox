# klox

![Build status](https://img.shields.io/github/workflow/status/mrjameshamilton/klox/CI) ![Coverage](.github/badges/jacoco.svg)

A Kotlin implementation of lox, the language from [Crafting Interpreters](https://craftinginterpreters.com/),
with a JVM backend built with [ProGuardCORE](https://github.com/guardsquare/proguard-core).

## Building

```shell
./gradlew build
```

The build task will execute all tests and create an output jar `lib/klox.jar`.

## Executing

A wrapper script `bin/klox` is provided for convenience in the `bin/` directory:

```shell
$ bin/klox --help
Usage: klox options_list
Arguments: 
    script -> Lox Script (optional) { String }
Options: 
    --outJar, -o -> output jar { String }
    --useInterpreter, -i -> use interpreter instead of JVM compiler when executing
    --debug -> enable debugging 
    --dumpClasses, -d -> dump textual representation of classes (instead of executing) 
    --args, -arg -> additional arguments to pass to the klox program { String }
    --help, -h -> Usage info 
```

Execute without a script for a REPL, otherwise the provided Lox script will be executed.
If a Lox script is provided, by default, the script will be executed by compiling it for
the JVM and executing the compiled code. The interpreter can be used instead to execute the script
by passing the `--useInterpreter` option (useful for comparing interpreted vs compiled runtime!).

The compiler can generate a jar for the given script by passing the `--outJar` option (in
this case the script will not be executed by `klox`) e.g.

```shell
$ bin/klox myScript.lox --outJar myScript.jar
$ java -jar myScript.jar
```

## Example Lox program

```c
fun hello() {
    print "Hello World";
}

hello();
```

Lox has control flow:

```c
if (1 == 2 and false or true) {
    print "true";
} else {
    print "false";
}

for (var i = 0; i < 10; i = i + 1) {
    print i;
}

while (true) {
    print "Looping forever";
}
```

Lox is object-oriented:

```c
class Person {
    init(name, surname) {
        this.name = name;
        this.surname = surname;
    }
    
    fullname() {
        return this.name + " " + this.surname + ".";
    }
    
    greet() {
        print "Hello, " + this.fullname();
    }
}

class Employee < Person {
    greet() {
        super.greet();
        print "Keep up the good work!";
    }
}

Employee("John", "Smith").greet(); 
// Hello, John Smith.
// Keep up the good work!
```

## Extra features

`klox` includes some features not implemented in the Crafting Interpreters implementation:

* Anonymous functions

```c
var foo = fun (x) {
    return x + 1;
};

print foo(1); // 2
```

* `break` and `continue` statements

```c
for (var i = 0; i < 10; i = i + 1) {
    if (i == 2) {
        continue;
    } else if (i == 5) {
        break;
    }
}
```
* property getters
```c
class Circle {
    init(radius) {
        this.radius = radius;
    }

    area {
        return 3.14159265359 * this.radius * this.radius;
    }
}

var circle = Circle(4);
print circle.area; // approx 50.266
```
* static class methods
```c
class Math {
    class square(n) {
        return n * n;
    }
}

print Math.square(3); // prints 9
```
* static class property getters
```c
class Math {
    class PI {
        return 3.14159265358979323846;
    }
}

print Math.PI; // approx. 50.265
```
* multi-line comments (nestable)
```c
/*
* Does something. /* nested comment */
*
* returns: nothing
*/
fun foo() {
    print "bar";
}
```

* `is` instance check

```c
class Super { }
class Foo < Super { }
class Bar { }

var foo = Foo();

print foo is Foo; // true
print foo is Super; // true
print foo is Bar; // false
```

* `toString` method

```c
class Greeter {
    init(name) {
        this.name = name;
    }
    
    toString() {
        return "Hello " + this.name;
    }
}

print Greeter("James"); // Hello James
```


## Klox standard library

Klox comes with a set of [standard library](src/main/resources/klox/) functions and classes.

### Lox built-in

For compatibility with `lox` the built-in, top-level `clock()` returns the current time in milliseconds.

### Object

Unlike lox, all klox classes extend from the root `Object`. `Object` is the only class with no super class.

### Array

The Array class represents a fixed-size array.

```c
class Array {
    init(size);
    get(index);
    set(index, value);
    length();
    map(function);
    reduce(initialValue, function);
    filter(function);
}

var array = Array(2);
array.set(0, "foo");
array.set(1, 123);
print array.get(0); // foo
print array.length(); // 2
```

### System

* `System.arg(number): string | nil` returns the nth argument passed to the program or nil if the argument is out of range.
* `System.exit(code)` exits the program with the given exit code.
* `System.fail(message)` exits the program with a non-zero exit code and the given message.

### Strings

* `String.length(string): number` returns the length of `string`.
* `String.substr(string, start, end): string | Error`
returns the substring of `string` between `start` (inclusive) and `end` (exclusive). Returns an `Error` on failure.
* `String.toNumber(string): number | Error`

### Files

```c
class File {
    init(path);
    readText();
    writeText(string);
    delete();
}
```

### Input/Output

Input/output is handled by sub-classes of `InputStream` / `OutputStream`.

```c
class FileInputStream < InputStream {
    init(file);
    /**
    * Returns the next byte from the stream or -1 if the end of the stream is reached.
    *
    * Returns an `Error` if there is an error.
    */
    readByte();

    /**
    * Returns the next character from the stream or nil if the end of the stream is reached.
    *
    * Returns an `Error` if there is an error.
    */
    readChar();

    /**
    * Closes the stream.
    *
    * Returns true or an `Error` if there is an error.
    */
    close();
}
```

```c
class FileOutputStream < OutputStream {
    init(file);
    /**
    * Writes a byte to the outputstream.
    *
    * Returns true or an `Error` if there is an error.
    */
    writeByte(b);

    /**
    * Writes a character to the outputstream.
    *
    * Returns true or an `Error` if there is an error.
    */
    writeChar(c);

    /**
    * Closes the stream.
    *
    * Returns true or an `Error` if there is an error.
    */
    close();
}
```
### Math

* `Math.PI` 3.141592653589793
* `Math.sqrt(number): number`

### Error handling

`Error` a class representing an error state. It can be combined with the `is` instance check to check
if a function had an error e.g.

```c
fun foo(a, b) {
    if (b == 0) return Error("Cannot divide by zero");
    else return a / b;
}

var result = foo(1, 0);

if (result is Error)
   print result.message;
else
   print result;
```
