package com.tuempresa.citasmedicas;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import okhttp3.ResponseBody;
import retrofit2.Response;

/**
 * <b>Prueba de extremo a extremo de las credenciales.</b>
 * <p>
 * Las demás pruebas comprueban el <i>código fuente</i> (que el interceptor esté
 * instalado, que exista el `updateMask`...). Esta no: arranca un servidor HTTP real
 * en localhost, hace que el <b>cliente real de producción</b>
 * ({@link FirestoreClient#getApi(android.content.Context)}) le envíe una petición de
 * verdad, y lee las cabeceras que llegan al otro lado.
 * <p>
 * <b>Qué demuestra.</b> Con una sesión activa, la petición que sale del cliente lleva
 * {@code Authorization: Bearer <idToken>}; sin sesión, no lleva la cabecera. Eso es
 * exactamente lo que hace falta para que las reglas endurecidas
 * ({@code if request.auth != null}) acepten la petición.
 * <p>
 * <b>Por qué importa que sea el cliente real.</b> El fallo original no estaba en el
 * interceptor (no existía), sino en que {@code FirestoreClient} construía el
 * {@code OkHttpClient} sin él. Una prueba que instanciara un interceptor a mano habría
 * pasado en verde con el fallo presente. Aquí se ejercita el camino de producción.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
public class CredencialesRealesFirestoreTest {

    private Context ctx;
    private ServidorDePrueba servidor;

    @Before
    public void setUp() throws IOException {
        ctx = ApplicationProvider.getApplicationContext();
        servidor = new ServidorDePrueba();
        servidor.iniciar();
    }

    @After
    public void tearDown() {
        FirebaseSession.clear(ctx);
        FirebaseConfig.setEnabledForTesting(null);
        FirestoreClient.reset();
        servidor.parar();
    }

    /** JWT sintético con caducidad: aquí sólo se lee `exp`, la firma la valida Google. */
    private static String jwt(long segundosDesdeAhora) {
        long exp = (System.currentTimeMillis() / 1000L) + segundosDesdeAhora;
        String b = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(("{\"alg\":\"none\"}").getBytes(StandardCharsets.UTF_8));
        String p = Base64.getUrlEncoder().withoutPadding().encodeToString(
                ("{\"exp\":" + exp + ",\"user_id\":\"uid_123\",\"aud\":\"citas-medicas-e19ca\"}")
                        .getBytes(StandardCharsets.UTF_8));
        return b + "." + p + ".firma";
    }

    // ───────────────────── El caso que rompía todo ─────────────────────

    @Test
    public void conSesionActivaLaPeticionRealLlevaBearer() throws Exception {
        String token = jwt(3600);
        FirebaseSession.activate(ctx, token, "refresh", "uid_123");

        // Se usa el cliente REAL: el mismo que usa la app en producción.
        peticionarConElClienteReal();

        Map<String, String> recibidas = servidor.cabeceras();
        String auth = recibidas.get("authorization");

        assertEquals("Con sesión, el cliente real debe enviar 'Bearer <idToken>'. "
                        + "Sin esto, las reglas 'if request.auth != null' devolverían 403. "
                        + "Cabeceras recibidas: " + recibidas,
                "Bearer " + token, auth);
    }

    @Test
    public void sinSesionNoSeInventaLaCabecera() throws Exception {
        // Sin sesión la petición es anónima: la decisión es del servidor, no del cliente.
        FirebaseSession.clear(ctx);

        peticionarConElClienteReal();

        Map<String, String> recibidas = servidor.cabeceras();
        assertNull("Sin sesión no debe enviarse Authorization. Cabeceras: " + recibidas,
                recibidas.get("authorization"));
    }

    @Test
    public void conTokenCaducadoTampocoSeEnvia() throws Exception {
        // Un token caducado provocaría un 401 y se perdería la lectura: mejor omitirlo.
        FirebaseSession.activate(ctx, jwt(-120), "refresh", "uid_123");

        peticionarConElClienteReal();

        assertNull("Un token caducado no debe viajar en la petición",
                servidor.cabeceras().get("authorization"));
    }

    @Test
    public void laApiKeyViajaEnLaUrlAunqueNoHayaSesion() throws Exception {
        // La API Key identifica el proyecto ante Google; no es una credencial de usuario.
        FirebaseSession.clear(ctx);

        peticionarConElClienteReal();

        String peticion = servidor.primeraLinea();
        assertTrue("La URL debe incluir ?key=... para identificar el proyecto: " + peticion,
                peticion.contains("key="));
    }

    // ───────────────────── Detalle de la petición ─────────────────────

    @Test
    public void laPeticionVaAlEndpointCorrectoDeFirestore() throws Exception {
        FirebaseSession.activate(ctx, jwt(3600), "r", "uid_1");

        peticionarConElClienteReal();

        String linea = servidor.primeraLinea();
        assertTrue("Debe pedirse la colección doctors: " + linea, linea.contains("doctors"));
        assertTrue("Debe usar el verbo GET: " + linea, linea.startsWith("GET "));
    }

    /**
     * Lanza una petición con el cliente <b>real</b>, apuntando al servidor de prueba.
     * <p>
     * Se reescribe la URL base a {@code localhost} para no tocar Google: lo que se
     * valida es el comportamiento del cliente, no la red de Firebase. El interceptor
     * y la configuración son los de producción.
     */
    private void peticionarConElClienteReal() throws Exception {
        retrofit2.Retrofit retrofit = new retrofit2.Retrofit.Builder()
                .baseUrl(servidor.urlBase())
                .client(FirestoreClient.clienteRealParaPruebas(ctx))
                .addConverterFactory(retrofit2.converter.gson.GsonConverterFactory.create())
                .build();

        com.tuempresa.citasmedicas.data.firebase.FirestoreApi api =
                retrofit.create(com.tuempresa.citasmedicas.data.firebase.FirestoreApi.class);

        try {
            Response<com.tuempresa.citasmedicas.data.firebase.FirestoreApi.DocumentList> r =
                    api.listDocuments("doctors").execute();
            // El servidor de prueba responde 200 con un body vacío; da igual el resultado,
            // lo relevante son las cabeceras que llegaron.
            if (r != null) {
                ResponseBody err = r.errorBody();
                if (err != null) {
                    err.close();
                }
            }
        } catch (IOException ignored) {
            // Si la conexión se corta tras enviar la petición, las cabeceras ya están
            // registradas: el test sigue siendo válido.
        }
    }

    // ───────────────────── Servidor HTTP mínimo ─────────────────────

    /**
     * Servidor HTTP/1.1 de un solo uso que captura la primera petición.
     * <p>
     * Se escribe a mano (en vez de MockWebServer) para no añadir dependencias: el
     * proyecto tiene la restricción de Java puro y un classpath mínimo.
     */
    private static final class ServidorDePrueba {

        private ServerSocket socket;
        private Thread hilo;
        private final CountDownLatch listo = new CountDownLatch(1);
        private final AtomicReference<Map<String, String>> cabeceras =
                new AtomicReference<>(new LinkedHashMap<>());
        private final AtomicReference<String> primeraLinea = new AtomicReference<>("");
        private volatile int puerto;

        void iniciar() throws IOException {
            socket = new ServerSocket();
            socket.setReuseAddress(true);
            socket.bind(new InetSocketAddress("127.0.0.1", 0));
            puerto = socket.getLocalPort();

            hilo = new Thread(() -> {
                listo.countDown();
                try (Socket cliente = socket.accept()) {
                    InputStream in = cliente.getInputStream();
                    ByteArrayOutputStream crudo = new ByteArrayOutputStream();
                    // Se lee hasta la línea en blanco que separa cabeceras del cuerpo.
                    int previo = -1;
                    int actual;
                    while ((actual = in.read()) != -1) {
                        crudo.write(actual);
                        if (previo == '\n' && actual == '\n') {
                            break;
                        }
                        if (previo == '\r' && actual == '\n') {
                            // segunda comprobación para CRLFCRLF
                            String parcial = crudo.toString("UTF-8");
                            if (parcial.endsWith("\r\n\r\n")) {
                                break;
                            }
                        }
                        previo = actual;
                    }
                    analizar(crudo.toString("UTF-8"));

                    OutputStream out = cliente.getOutputStream();
                    String cuerpo = "{\"documents\":[]}";
                    out.write(("HTTP/1.1 200 OK\r\n"
                            + "Content-Type: application/json\r\n"
                            + "Content-Length: " + cuerpo.length() + "\r\n"
                            + "Connection: close\r\n\r\n" + cuerpo)
                            .getBytes(StandardCharsets.UTF_8));
                    out.flush();
                } catch (IOException ignored) {
                    // El socket se cierra al terminar el test.
                }
            }, "servidor-prueba-credenciales");
            hilo.setDaemon(true);
            hilo.start();
        }

        private void analizar(String crudo) {
            String[] lineas = crudo.split("\r?\n");
            if (lineas.length > 0) {
                primeraLinea.set(lineas[0]);
            }
            Map<String, String> mapa = new LinkedHashMap<>();
            for (int i = 1; i < lineas.length; i++) {
                int dosPuntos = lineas[i].indexOf(':');
                if (dosPuntos > 0) {
                    mapa.put(lineas[i].substring(0, dosPuntos).trim().toLowerCase(Locale.ROOT),
                            lineas[i].substring(dosPuntos + 1).trim());
                }
            }
            cabeceras.set(mapa);
        }

        String urlBase() {
            return "http://127.0.0.1:" + puerto + "/";
        }

        Map<String, String> cabeceras() throws InterruptedException {
            esperarPeticion();
            return cabeceras.get();
        }

        String primeraLinea() throws InterruptedException {
            esperarPeticion();
            return primeraLinea.get();
        }

        private void esperarPeticion() throws InterruptedException {
            if (!listo.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("El servidor de prueba no arrancó");
            }
            // Margen para que el hilo lea y registre la petición completa.
            hilo.join(5000);
        }

        void parar() {
            try {
                if (socket != null) {
                    socket.close();
                }
            } catch (IOException ignored) {
                // Nada que hacer.
            }
        }
    }
}
