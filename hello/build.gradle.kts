import org.github.vvlevchenko.gradle.make.*


plugins {
    `make`
}


make {
    environment {
        "CC"("clang")
    }
    suffixes {
        (".c" to ".o")(this, ref("CC"), ref("CPPFLAGS"), ref("CFLAGS"), "-c", "-o",
            *iref("target"), *iref("sources"))
    }
    /**
     * hello:
     *   echo "hello world"
     */
    "hello"(true) {
        shell("echo", "hello wworld")
    }
        /**
         * blah: blah.o
         * cc blah.o -o blah # Runs third
         *
         * blah.o: blah.c
         * cc -c blah.c -o blah.o # Runs second
         *
         * blah.c:
         * echo "int main() { return 0; }" > blah.c
         */
    "blach"(true, "blach.o") {
        shell(ref("CC")!!, *iref("sources"), "-o", *iref("target"))
    }
    "blach.c"{
        shell("echo", "int main(){ return 0; }")
    }

}

