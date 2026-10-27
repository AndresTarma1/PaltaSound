package example.nucleus.utils.cipher

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class NativePlayerScriptAnalyzerTest {
    @Test
    fun extractsFromSyntheticPlayer() {
        val fakePlayer = """
            var _yt_player={};(function(g){
              var window=this;
              Foo=function(a,b,c){b=b===void 0?"":b;c=c===void 0?"":c;a=new g.BarClass(a,!0);a.set("alr","yes");c&&(c=SigFn(7,42,Pre(9,11,c)),a["sig"](b,Post(3,99,c)));return a};
              Baz=function(a){try{var b=(new g.BarClass(a,!0)).get("n")}catch(e){}return a};
            })(_yt_player);
        """.trimIndent()

        val analysis = NativePlayerScriptAnalyzer.analyze(fakePlayer)
        assertEquals("BarClass", analysis.nClassName)
        assertEquals("SigFn(7,42,Pre(9,11,input))", analysis.sigExpression)
    }

    @Test
    fun throwsWhenNClassMissing() {
        val fakePlayer = "var x=1; function f(a,b,c){ a.set(\"alr\",\"yes\"); c&&(c=G(1,2,H(3,4,c))); return a; }"
        assertFailsWith<CipherException> {
            NativePlayerScriptAnalyzer.analyze(fakePlayer)
        }
    }

    @Test
    fun throwsWhenSigMissing() {
        val fakePlayer = "var x=1; (new g.Fl(a,!0)).get(\"n\");"
        assertFailsWith<CipherException> {
            NativePlayerScriptAnalyzer.analyze(fakePlayer)
        }
    }

    @Test
    fun skipsDecoyAlrMarkersBeforeRealSigFunction() {
        // Forma player 6b2fce74+: otros `set("alr","yes")` (redirector/DD) aparecen ANTES de
        // la función de firma, y el call de firma usa acceso computado `a[ba[19]]`.
        val fakePlayer = """
            var _yt_player={};(function(g){
              g.GO=function(a,b,c){var d=new DD(b);d.get("alr")||d.set("alr","yes");a.B&&EMa(d,a.B)};
              P0a=function(a,b){c=MU(a,"x");c.set("alr","yes");c.set("id","1");return c};
              YQ=function(a,b,c){b=b===void 0?"":b;c=c===void 0?"":c;a=new g.yb(a,!0);a.set("alr","yes");c&&(c=zb(1,6474,Ab(3,820,c)),a[ba[19]](b,Cb(41,8418,c)));return a};
              vSa=function(a){try{var b=(new g.yb(a,!0)).get("n")}catch(e){}return a};
            })(_yt_player);
        """.trimIndent()

        val analysis = NativePlayerScriptAnalyzer.analyze(fakePlayer)
        assertEquals("yb", analysis.nClassName)
        assertEquals("zb(1,6474,Ab(3,820,input))", analysis.sigExpression)
    }
}
