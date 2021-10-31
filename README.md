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
* static class methods
```c
class Math {
    class square(n) {
        return n * n;
    }
}

print Math.square(3); // prints 9
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

### Built-in functions

* `clock` returns the current time in milliseconds.
* `strlen(string)` returns the length of `string`.