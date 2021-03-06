# klox

![Build status](https://img.shields.io/github/workflow/status/mrjameshamilton/klox/CI) ![Coverage](.github/badges/jacoco.svg)

A Kotlin implementation of lox, the language from [Crafting Interpreters](https://craftinginterpreters.com/),
with a JVM backend built with [ProGuardCORE](https://github.com/guardsquare/proguard-core).

The `klox` language is a superset of `lox` and includes features not implemented in the Crafting Interpreters `lox` implementation.

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

## `klox` features

### Do-while loops

```c
do {
    print "Do-while loop";
} while (false);
```


### Arrays

Arrays can be created with the `[]` syntax

```c
var arr = [1, 2, 3];
```

The `[]` syntax is also used to access array elements:

```c 
print arr[0]; // 1
```

Python-style slices are also supported:

```c
print [1, 2, 3][0:2]; // [1, 2]
print [1, 2, 3][:]; // [1, 2, 3]
print [1, 2, 3][::-1]; // [3, 2, 1]
```

These are actually syntactic sugar for the `init`, `get`, `set` and `slice` methods on the `Array` object:

```c 
// var arr = [1, 2, 3];
var arr = Array(3);
arr.set(0, 1);
arr.set(1, 2);
arr.set(2, 3);

print arr.get(0); // print arr[0]; // 1
arr.set(0, 1); // arr[0] = 1;
```

### `get`, `set` and `slice` methods

Like `Array`, any class can define `get`, `set` and `slice` methods to take advantage of the `[]` syntax:

```c
class Foo {
    init(value) {
        this.value = value;
    }
    
    get(index) {
        if (index == 0) {
            return this.value;
        } else {
            return null;
        }
    }
    
    set(index, value) {
        this.value = index + ": " + value;
    }
}

var foo = Foo("foo");
print foo[0]; // foo
foo[0] = "bar";
print foo[0]; // bar
```

### Destructuring declarations

```c
var (foo, bar) = ["foo", "bar"];
print foo; // foo
print bar; // bar
```

Destructuring declarations are syntactic sugar for `get` methods - any class can implement
`get(index)` to take advantage of destructuring declarations.

Underscore can be used to ignore a value:

```c
var (_, bar) = ["foo", "bar"];
```

### For-in loops

A class implementing a method `iterator` that returns an `Iterator` object can be used as a for-in loop.
`Array` and `ArrayList` are examples of classes that implement this method.

```c
for (var x in [1, 2, 3]) {
    print x;
}
```

You can also use destructuring declarations with for-in loops:

```c
for (var (a, b) in [[1, 2], [3, 4]]) {
    print a + " " + b;
}
// 1 2
// 3 4
```

The `Iterator` class is defined as the following:

```c
class Iterator {
    hasNext(): boolean
    next(): value | nil
}
```

### Range operator `..`

The range operator invokes `leftHandSide.rangeTo(rightHandSide)` on an object, which should return an `Range` object. Numbers
and characters are built-in types that work with `..` but custom classes can also implement `rangeTo`.

```c
for (var x in 0..10) { // equivilant: for (var x in NumberRange(0, 10))
    print x;
}

for (var c in "A".."Z") { // equivilant: for (var c in CharacterRange(0, 10))
    print c;
}

class Foo {
    rangeTo(x) = NumberRange(0, x)
}

var foo = Foo();
for (var x in foo..10) {
    print x;
}
```

### Multiple declarations in the same statement

```c
var a = 1, b = 2;
```

### (Post/pre)fix increment and decrement operators

Numerical values can be incremented and decremented:

```c
var i = 0;
i++;
++i;
i--;
--i;
```

### Anonymous functions

```c
var foo = fun (x) {
    return x + 1;
};

print foo(1); // 2
```

### Single-expression functions

```
fun foo() = 1;
fun bar() = 2 // optional ;
fun baz() = foo() + bar()
```

### Classes without bodies

```
class Foo
class Bar < Foo
print Bar is Foo;
```

### Class header inline initializers

```
class Foo(x)
var foo = Foo(1);
print foo.x; // 1

class Base(foo)
class Bar(x) < Base(x)
var bar = Bar(1);
print bar.foo; // 1
```

### Data classes

Data classes implement boilerplate code for `get`, `equals`, `hashCode` and `toString`:

```
data class Foo(x)
print Foo(1); // Foo(x = 1)
```

### Operator overloading

The following binary operators can be overloaded by declaring a method with a particular name:

| Operator | Method |
|----------|--------|
| `+`      | plus(other)   |
| `-`      | minus(other)  |
| `/`      | div(other)    |
| `*`      | times(other)  |
| `%`      | rem(other)    |
| `==`     | equals(other) |
| `..`     | rangeTo(other) |


### Null-safe operator

The null-safe operator `?` can be used to chain property accesses without needing to
explicitly check for null and without the programming exiting if the property is not found:

```c
var x = foo?.bar?.baz;

// equivalent to:

var x = foo.bar;
if (x.baz != nil) x = x.baz;
```

### Early-return operator

The early-return operator `!?` can be used to return from a function if an error is thrown:

```c
fun foo() {
    // if bar returns `Error`, then the function will return early with that `Error`.
    var x = bar()!?;
    print "Bar success"; // control-flow will only reach this line if bar() returns successfully
    return Ok(x); 
}

var (result, error) = foo();
```

### comma operator

The comma operator is used to chain multiple expressions together, the left-hand side of the
expression is evaluated & its result discarded then right-hand side is evaluated and returned.

```c
print "foo", "bar"; // prints bar
```

### modulo operator, power operator

```c
print 5 % 2; // prints 1
```

```c
print 3 ** 2; // prints 9
```

### bitwise operators

`&` (and), `|` (or), `^` (xor) and `~` (complement), `<<` (left shift), `>>` (right shift) and `>>>` (unsigned right shift) bitwise operators are supported.

### `break` and `continue` statements

```c
for (var i = 0; i < 10; i = i + 1) {
    if (i == 2) {
        continue;
    } else if (i == 5) {
        break;
    }
}
```
### property getters
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
### static class methods
```c
class Math {
    static square(n) {
        return n * n;
    }
}

print Math.square(3); // prints 9
```
### static class property getters
```c
class Math {
    static PI {
        return 3.14159265358979323846;
    }
}

print Math.PI; // approx. 50.265
```
### multi-line comments (nestable)
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

### `is` instance check

```c
class Super { }
class Foo < Super { }
class Bar { }

var foo = Foo();

print foo is Foo; // true
print foo is Super; // true
print foo is Bar; // false
```

### `toString` method

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

### `equals` method

The root class `Object` implements `equals(other)` which returns `true` if the two objects are the same instance.
The `==` operator calls `obj.equals(other)` if `obj` is an instance of `Object`.

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
    slice(start, stop, step);
    map(function);
    reduce(initial, function);
    filter(function);
    forEach(function);
    forEachIndexed(function);
}

var array = Array(2);
array.set(0, "foo");
array.set(1, 123);
print array.get(0); // foo
print array.length(); // 2
```

The static method `System.arraycopy` can be used to efficiently copy one array to another.

### System

* `System.arg(number): string | nil` returns the nth argument passed to the program or nil if the argument is out of range.
* `System.exit(code)` exits the program with the given exit code.
* `System.fail(message)` exits the program with a non-zero exit code and the given message.
* `System.arraycopy(src, srcPos, dest, destPos, length)`

### Strings

* `String.length(string): number` returns the length of `string`.
* `String.substring(string, start, end): string | Error`
* `String.indexOf(string, substring, start): number`
returns the substring of `string` between `start` (inclusive) and `end` (exclusive). Returns an `Error` on failure.
* `String.toNumber(string): number | Error` converts a string to a number e.g. "5" -> 5.

### Characters

* `Character.toCharCode(c): number` returns the numeric value of `c` e.g. "A" -> 65.
* `Character.fromCharCode(n): c` returns the character value of `n` e.g. 65 -> "A".

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
* `Math.ceil(number): number`
* `Math.floor(number): number`
* `Math.round(number): number`
* `Math.min(number): number`
* `Math.max(number): number`
* `Math.abs(number): number`

### Error handling

Functions can return a `Result` object of which there are two variants `Ok` and `Error` (inspired by Rust).

```c
fun foo(a, b) {
    if (b == 0) return Error("Cannot divide by zero");
    else return Ok(a / b);
}

var (result, error) = foo(1, 0);

print result; // expect: nil
print error; // expect: Cannot divide by zero
```

`Result` provides convenience functions for working with `Ok` or `Error` results, e.g.

```c
foo(1, 0).orFail(); // will exit the program with the error message if there is an error.
foo(1, 0).orNil(); // returns nil if there is an error.
```

These can be chained e.g.

```c
var err = file.writeText("Hello World").andThen(fun (x) {
    print "F ile written"; // expect: File written
    return file.readText().andThen(fun (text) {
        print "File read"; // expect: File read
        print text; // expect: Hello World
        return file.delete().andThen(fun (x) {
            print "File deleted"; // expect: File deleted
        });
    });
});

print err; // expect: nil
```

The `!?` operator can be used with functions that return `Result` to return
early if there is an error. If the `Result` is an `Error` then the calling 
function returns early with the `Error` instance. Otherwise, the value of an
`Ok` result is unwrapped.

```
fun foo() {
    var a = doSomething()!?;
    var b = doAnotherThing()!?;
    return Ok(a + b);
}
```

This allows chaining of potentially error throwing functions:

```
fun foo(x, y, z) {
    return A().a(x)!?.b(y)!?.c(z)!?;
}
```
