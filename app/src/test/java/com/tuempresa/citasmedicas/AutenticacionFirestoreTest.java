package com.tuempresa.citasmedicas;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import com.tuempresa.citasmedicas.data.RepositoryProvider;
import com.tuempresa.citasmedicas.data.firebase.AuthHeaderInterceptor;
import com.tuempresa.citasmedicas.data.firebase.FirebaseConfig;
import com.tuempresa.citasmedicas.data.firebase.FirebaseSession;
import com.tuempresa.citasmedicas.data.firebase.FirestoreClient;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Base64;

/**
 * Pruebas de la <b>autenticación de las peticiones a Firestore</b>.
 * <p>
 * <b>El fallo que cubren.</b> El cliente de Firestore se construía sin ninguna
 * credencial. Todo funcionaba sólo porque las Reglas de Firestore estaban en
 * {@code allow read, write: if true}. En el momento de endurecerlas —el paso previsto
 * antes de entregar—, la app habría empezado a recibir {@code 403} en todas las
 * lecturas y a no subir las citas, <b>sin ningún error visible</b>: el catálogo habría
 * caído al respaldo local y el usuario no habría notado nada raro.
 * <p>
 * Estos tests fijan el contrato: el cliente <b>tiene</b> que llevar
 * {@link AuthHeaderInterceptor} y el token tiene que guardarse y caducar bien.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
public class AutenticacionFirestoreTest {

    private Context ctx;

    @Before
    public void setUp() {
        ctx = ApplicationProvider.getApplicationContext();
        FirebaseSession.clear(ctx);
        FirestoreClient.reset();
    }

    @After
    public void tearDown() {
        FirebaseSession.clear(ctx);
        FirestoreClient.reset();
        FirebaseConfig.setEnabledForTesting(null);
    }

    // ───────────────────────── Token y caducidad ─────────────────────────

    /** Un JWT con la caducidad indicada (sin firma válida: aquí sólo se lee `exp`). */
    private static String jwtConCaducidad(long segundosDesdeAhora) {
        long exp = (System.currentTimeMillis() / 1000L) + segundosDesdeAhora;
        String cabecera = b64("{\"alg\":\"none\",\"typ\":\"JWT\"}");
        String payload = b64("{\"exp\":" + exp + ",\"user_id\":\"uid_123\"}");
        return cabecera + "." + payload + ".firma";
    }

    private static String b64(String texto) {
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(texto.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    public void unTokenVigenteSeAcepta() {
        assertTrue("Un token que caduca dentro de una hora debe servir",
                FirebaseSession.esTokenVigente(jwtConCaducidad(3600)));
    }

    @Test
    public void unTokenCaducadoSeRechaza() {
        // Enviarlo provocaría un 401 y se perdería la lectura; mejor no mandarlo.
        assertFalse("Un token ya caducado no debe usarse",
                FirebaseSession.esTokenVigente(jwtConCaducidad(-60)));
    }

    @Test
    public void unTokenQueCaducaEnSegundosSeRechazaPorElMargen() {
        // Margen de 60 s: evita que el token expire mientras la petición está en vuelo.
        assertFalse("Un token a punto de caducar no debe usarse",
                FirebaseSession.esTokenVigente(jwtConCaducidad(30)));
    }

    @Test
    public void unTokenBasuraNoRompeNada() {
        assertFalse(FirebaseSession.esTokenVigente(null));
        assertFalse(FirebaseSession.esTokenVigente(""));
        assertFalse(FirebaseSession.esTokenVigente("sin.puntos"));
        assertFalse(FirebaseSession.esTokenVigente("a.b.c"));
        assertFalse("Base64 válido pero sin 'exp'", FirebaseSession.esTokenVigente(
                b64("{}") + "." + b64("{\"sub\":\"x\"}") + ".f"));
    }

    // ───────────────────────── Sesión persistida ─────────────────────────

    @Test
    public void laSesionSeGuardaYSeRecupera() {
        String jwt = jwtConCaducidad(3600);
        FirebaseSession.activate(ctx, jwt, "refresh_abc", "uid_123");

        assertEquals("El token debe recuperarse tal cual", jwt, FirebaseSession.token(ctx));
        assertEquals("uid_123", FirebaseSession.uid(ctx));
        assertTrue(FirebaseSession.isActive(ctx));
    }

    @Test
    public void cerrarSesionBorraElToken() {
        FirebaseSession.activate(ctx, jwtConCaducidad(3600), "r", "uid_1");
        assertTrue(FirebaseSession.isActive(ctx));

        FirebaseSession.clear(ctx);

        assertNull("Sin token no hay cabecera Authorization", FirebaseSession.token(ctx));
        assertFalse(FirebaseSession.isActive(ctx));
    }

    @Test
    public void unTokenGuardadoQueYaCaducoNoSeDevuelve() {
        FirebaseSession.activate(ctx, jwtConCaducidad(-10), "r", "uid_1");
        assertNull("Guardado pero caducado: no debe devolverse", FirebaseSession.token(ctx));
        assertFalse(FirebaseSession.isActive(ctx));
    }

    // ───────────────────────── Interceptor ─────────────────────────

    @Test
    public void elInterceptorInyectaLaApiKeyCuandoFalta() throws IOException {
        AuthHeaderInterceptor interceptor = new AuthHeaderInterceptor(ctx, "AIzaSyPRUEBA");

        okhttp3.Request original = new okhttp3.Request.Builder()
                .url("https://firestore.googleapis.com/v1/projects/p/databases/(default)/documents/doctors")
                .build();

        okhttp3.Request procesada = aplicar(interceptor, original);

        assertNotNull(procesada.url().queryParameter("key"));
        assertEquals("AIzaSyPRUEBA", procesada.url().queryParameter("key"));
    }

    @Test
    public void elInterceptorAnadeElBearerConSesionActiva() throws IOException {
        String jwt = jwtConCaducidad(3600);
        FirebaseSession.activate(ctx, jwt, "r", "uid_1");

        AuthHeaderInterceptor interceptor = new AuthHeaderInterceptor(ctx, "AIzaSyPRUEBA");
        okhttp3.Request procesada = aplicar(interceptor, new okhttp3.Request.Builder()
                .url("https://firestore.googleapis.com/v1/projects/p/databases/(default)/documents/doctors?key=AIzaSyPRUEBA")
                .build());

        assertEquals("Bearer " + jwt, procesada.header("Authorization"));
    }

    @Test
    public void elInterceptorNoFalsificaSesionSinToken() throws IOException {
        // Sin sesión la petición debe seguir siendo anónima: la decisión es del servidor.
        AuthHeaderInterceptor interceptor = new AuthHeaderInterceptor(ctx, "AIzaSyPRUEBA");
        okhttp3.Request procesada = aplicar(interceptor, new okhttp3.Request.Builder()
                .url("https://firestore.googleapis.com/v1/projects/p/databases/(default)/documents/doctors")
                .build());

        assertNull("Sin token no debe inventarse una cabecera",
                procesada.header("Authorization"));
    }

    /** Ejecuta el interceptor con una cadena falsa: se queda con la petición resultante. */
    private static okhttp3.Request aplicar(AuthHeaderInterceptor interceptor,
                                           okhttp3.Request original) throws IOException {
        final okhttp3.Request[] capturada = new okhttp3.Request[1];
        okhttp3.Interceptor.Chain cadena = new okhttp3.Interceptor.Chain() {
            @Override
            public okhttp3.Request request() {
                return original;
            }

            @Override
            public okhttp3.Response proceed(okhttp3.Request request) {
                capturada[0] = request;
                return new okhttp3.Response.Builder()
                        .request(request)
                        .protocol(okhttp3.Protocol.HTTP_1_1)
                        .code(200)
                        .message("OK")
                        .body(okhttp3.ResponseBody.create("{}",
                                okhttp3.MediaType.parse("application/json")))
                        .build();
            }

            @Override
            public okhttp3.Connection connection() {
                return null;
            }

            @Override
            public okhttp3.Call call() {
                return null;
            }

            @Override
            public int connectTimeoutMillis() {
                return 0;
            }

            @Override
            public okhttp3.Interceptor.Chain withConnectTimeout(int timeout, java.util.concurrent.TimeUnit unit) {
                return this;
            }

            @Override
            public int readTimeoutMillis() {
                return 0;
            }

            @Override
            public okhttp3.Interceptor.Chain withReadTimeout(int timeout, java.util.concurrent.TimeUnit unit) {
                return this;
            }

            @Override
            public int writeTimeoutMillis() {
                return 0;
            }

            @Override
            public okhttp3.Interceptor.Chain withWriteTimeout(int timeout, java.util.concurrent.TimeUnit unit) {
                return this;
            }
        };
        interceptor.intercept(cadena);
        return capturada[0];
    }

    // ───────────────────────── Contrato del cliente ─────────────────────────

    @Test
    public void elClienteDeFirestoreUsaElInterceptorDeCredenciales() throws IOException {
        // Regresión: sin esta comprobación, quitar el interceptor volvería a dejar la
        // app dependiendo de que las reglas sean públicas, y nada fallaría en los tests.
        String fuente = leerFuente(
                "app/src/main/java/com/tuempresa/citasmedicas/data/firebase/FirestoreClient.java",
                "src/main/java/com/tuempresa/citasmedicas/data/firebase/FirestoreClient.java");

        assertTrue("FirestoreClient debe instalar AuthHeaderInterceptor",
                fuente.contains("new AuthHeaderInterceptor("));
        assertTrue("El interceptor debe construirse con la API Key configurada",
                fuente.contains("FirebaseConfig.apiKey()"));
        assertTrue("Debe existir la variante con Context, que es la que puede leer el token",
                fuente.contains("getApi(@NonNull Context context)"));
    }

    @Test
    public void losViewModelsPasanContextoParaPoderAutenticar() throws IOException {
        String doctorVm = leerFuente(
                "app/src/main/java/com/tuempresa/citasmedicas/viewmodel/DoctorViewModel.java",
                "src/main/java/com/tuempresa/citasmedicas/viewmodel/DoctorViewModel.java");
        String patientVm = leerFuente(
                "app/src/main/java/com/tuempresa/citasmedicas/viewmodel/PatientViewModel.java",
                "src/main/java/com/tuempresa/citasmedicas/viewmodel/PatientViewModel.java");

        assertTrue("DoctorViewModel debe ser AndroidViewModel para tener Context",
                doctorVm.contains("extends AndroidViewModel"));
        assertTrue("DoctorViewModel debe pasar el contexto al provider",
                doctorVm.contains("RepositoryProvider.doctors("));
        assertTrue("PatientViewModel debe ser AndroidViewModel para tener Context",
                patientVm.contains("extends AndroidViewModel"));
        assertTrue("PatientViewModel debe pasar el contexto al provider",
                patientVm.contains("RepositoryProvider.patients("));
    }

    @Test
    public void elAuthGuardaLaSesionTrasUnLoginCorrecto() throws IOException {
        String fuente = leerFuente(
                "app/src/main/java/com/tuempresa/citasmedicas/data/firebase/FirebaseAuthRepository.java",
                "src/main/java/com/tuempresa/citasmedicas/data/firebase/FirebaseAuthRepository.java");

        assertTrue("Tras el login debe persistirse el idToken",
                fuente.contains("FirebaseSession.activate("));
        assertTrue("El guardado debe ocurrir en el camino de éxito",
                fuente.contains("recordarSesion(response.body())"));
    }

    @Test
    public void elProviderTieneVarianteConContextoParaCatalogoYPacientes() {
        // Contrato: si alguien vuelve a quitar el parámetro, no se podría autenticar.
        assertNotNull(RepositoryProvider.doctors(ctx));
        assertNotNull(RepositoryProvider.patients(ctx));
        assertNotNull(RepositoryProvider.auth(ctx));
        assertNotNull(RepositoryProvider.appointments(ctx));
    }

    // ───────────────────────── Utilidades ─────────────────────────

    /** Lee un fuente Java probando dos rutas relativas (Gradle e IDE). */
    private static String leerFuente(String... rutas) throws IOException {
        for (String ruta : rutas) {
            File f = new File(ruta);
            if (f.isFile()) {
                return new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
            }
        }
        throw new IOException("No se encontró el fuente. Probado: " + String.join(", ", rutas));
    }

    // ───────────────── Renovación del token (caducidad a la hora) ─────────────────

    @Test
    public void sinRefreshTokenNoSePuedeRenovarPeroNoRompe() {
        // Si sólo hay un idToken caducado y no hay refreshToken, no se inventa nada:
        // devolver null hace que la petición salga anónima en vez de con basura.
        FirebaseSession.activate(ctx, jwtConCaducidad(-120), null, "uid_1");
        assertNull("Sin refreshToken no debe devolverse un token caducado",
                FirebaseSession.token(ctx));
        assertTrue("Pero la sesión guardada debe seguir siendo detectable",
                FirebaseSession.hasStoredSession(ctx));
    }

    @Test
    public void hasStoredSessionDistingueCaducadoDeNoHaberIniciado() {
        // Diagnóstico útil: "nunca inició sesión" y "caducó" son problemas distintos.
        FirebaseSession.clear(ctx);
        assertFalse("Sin nada guardado no hay sesión", FirebaseSession.hasStoredSession(ctx));

        FirebaseSession.activate(ctx, jwtConCaducidad(-120), "refresh_abc", "uid_1");
        assertTrue("Con refreshToken guardado la sesión existe aunque el token caduque",
                FirebaseSession.hasStoredSession(ctx));
    }

    @Test
    public void elTokenVigenteSeDevuelveSinLlamarALaRed() {
        // Camino rápido: si el token sirve, no hay petición de renovación.
        String jwt = jwtConCaducidad(3600);
        FirebaseSession.activate(ctx, jwt, "refresh_abc", "uid_1");
        assertEquals(jwt, FirebaseSession.token(ctx));
    }

    @Test
    public void laRenovacionUsaElEndpointCorrectoDeGoogle() throws IOException {
        // Regresión: el endpoint de refresh_token es DISTINTO al de signIn
        // (securetoken vs identitytoolkit). Confundirlos daría 404 en silencio.
        String fuente = leerFuente(
                "app/src/main/java/com/tuempresa/citasmedicas/data/firebase/FirebaseSession.java",
                "src/main/java/com/tuempresa/citasmedicas/data/firebase/FirebaseSession.java");

        assertTrue("Debe usar securetoken.googleapis.com/v1/token",
                fuente.contains("https://securetoken.googleapis.com/v1/token"));
        assertTrue("Debe enviar grant_type=refresh_token",
                fuente.contains("grant_type"));
        assertTrue("Debe guardar el refreshToken rotado; si no, la sesión se pierde",
                fuente.contains("nuevoRefresh.isEmpty() ? refresh : nuevoRefresh"));
    }
}
