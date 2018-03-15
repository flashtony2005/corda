package net.corda.nodeapi.internal.serialization.carpenter

import net.corda.nodeapi.internal.serialization.AllWhitelist
import net.corda.nodeapi.internal.serialization.amqp.*
import org.junit.Test
import kotlin.reflect.jvm.jvmName

// Simple way to ensure we end up trying to carpent a class, "remove" it from the class loader (if only
// actually doing that was simple)
class TestClassLoader (private var exclude: List<String>) : ClassLoader() {
    override fun loadClass(p0: String?, p1: Boolean): Class<*> {
        if (p0 in exclude) {
            throw ClassNotFoundException("Pretending we can't find class $p0")
        }

        return super.loadClass(p0, p1)
    }
}

// Create a custom serialization factory where we need to be able to both specify a carpenter
// but also ahve the class loader used be the carpenter be substantially different from the
// one used by the factory so as to ensure we can control their behaviour independently.
class TestFactory(override val classCarpenter: ClassCarpenter, cl: ClassLoader)
    : SerializerFactory (classCarpenter.whitelist, cl)

class CarpenterExceptionTests {
    companion object {
        val VERBOSE: Boolean get() = false
    }

    @Test
    fun carpenterExcpRethrownAsNSE() {
        data class C2 (val i: Int)
        data class C1 (val i: Int, val c: C2)

        // We need two factories to ensure when we deserialize the blob we don't just use the serializer
        // we built to serialise things
        val ser = TestSerializationOutput(VERBOSE, testDefaultFactory()).serialize(C1(1, C2(2)))

        // Our second factory is "special"
        // The class loader given to the factory rejects the outer class, this will trigger an attempt to
        // carpent that class up. However, when looking at the fields specified as properties of that class
        // we set the class loader of the ClassCarpenter to reject one of them, resulting in a CarpentryError
        // which we then  want the code to wrap in a NotSerializeableException
        val cc = ClassCarpenter(TestClassLoader(listOf(C2::class.jvmName)), AllWhitelist)
        val factory = TestFactory(cc, TestClassLoader(listOf(C1::class.jvmName)))

        DeserializationInput(factory).deserialize(ser)
    }
}