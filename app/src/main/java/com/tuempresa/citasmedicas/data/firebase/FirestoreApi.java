package com.tuempresa.citasmedicas.data.firebase;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.gson.annotations.SerializedName;

import java.util.List;
import java.util.Map;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.Path;

/**
 * API REST de <b>Cloud Firestore</b> (v1), definida con Retrofit.
 * <p>
 * <b>Java puro.</b> Igual que con Authentication, se usa REST en lugar del SDK
 * {@code firebase-firestore} (que arrastra {@code kotlin-stdlib} y
 * {@code kotlinx-coroutines}). Zero dependencias nuevas: Retrofit y Gson ya estaban.
 * <p>
 * <b>Forma de los datos.</b> Firestore no devuelve JSON plano: cada campo viene
 * envuelto en un objeto con un tipo ({@code stringValue}, {@code integerValue},
 * {@code timestampValue}...). Por eso los documentos se modelan como
 * {@code Map<String, Value>} y la conversión la hace {@code FirestoreMapper}.
 */
public interface FirestoreApi {

    /**
     * Lista los documentos de una colección.
     * <p>
     * {@code pageSize} evita traer colecciones enteras de golpe (Firestore devuelve
     * 20 por defecto).
     */
    @GET("{collection}")
    Call<DocumentList> listDocuments(@Path("collection") String collection);
    // Nota: la collection se pasa como path relativo a la base URL configurada en
    // FirebaseConfig.firestoreBaseUrl(), que ya incluye projects/{id}/databases/(default)/documents/

    /** Lee un documento concreto por su id. */
    @GET("{collection}/{documentId}")
    Call<FirestoreDocument> getDocument(@Path("collection") String collection,
                                        @Path("documentId") String documentId);

    /**
     * Crea un documento con id generado automáticamente por Firestore.
     * <p>
     * Es un POST a la colección: Firestore asigna el id y lo devuelve en {@code name}.
     */
    @POST("{collection}")
    Call<FirestoreDocument> createDocument(@Path("collection") String collection,
                                           @NonNull @Body FirestoreDocument body);

    /**
     * Consulta estructurada (equivalente a {@code where(...)}).
     * <p>
     * Se usa {@code :runQuery} sobre la base de la colección. Permite filtrar sin
     * necesitar índices compuestos si sólo hay una igualdad.
     */
    @POST("{collection}:runQuery")
    Call<List<QueryResult>> runQuery(@Path("collection") String collection,
                                     @NonNull @Body QueryRequest body);

    // ───────────────────────── Modelos de Firestore ─────────────────────────

    /** Respuesta de listar documentos. */
    class DocumentList {
        @SerializedName("documents")
        public List<FirestoreDocument> documents;
    }

    /** Un documento de Firestore. */
    class FirestoreDocument {
        /** Ruta completa: {@code projects/{p}/databases/(default)/documents/{col}/{id}} */
        @SerializedName("name")
        public String name;

        /** Campos del documento. */
        @SerializedName("fields")
        public Map<String, Value> fields;

        @SerializedName("createTime")
        public String createTime;

        @SerializedName("updateTime")
        public String updateTime;
    }

    /**
     * Un valor de Firestore.
     * <p>
     * Sólo se declaran los tipos que usa la app. Se usan envoltorios ({@link String},
     * {@link Long}, {@link Double}, {@link Boolean}) para poder distinguir "campo
     * ausente" de "campo con valor cero".
     */
    class Value {
        @SerializedName("stringValue")
        public String stringValue;

        @SerializedName("integerValue")
        public String integerValue;

        @SerializedName("doubleValue")
        public Double doubleValue;

        @SerializedName("booleanValue")
        public Boolean booleanValue;

        @SerializedName("timestampValue")
        public String timestampValue;

        @SerializedName("nullValue")
        public String nullValue;

        @SerializedName("arrayValue")
        public ArrayValue arrayValue;

        @SerializedName("mapValue")
        public MapValue mapValue;
    }

    class ArrayValue {
        @SerializedName("values")
        public List<Value> values;
    }

    class MapValue {
        @SerializedName("fields")
        public Map<String, Value> fields;
    }

    // ───────────────────────── Consultas ─────────────────────────

    /** Cuerpo de una consulta estructurada ({@code runQuery}). */
    class QueryRequest {
        @SerializedName("structuredQuery")
        public StructuredQuery structuredQuery;

        public QueryRequest(@NonNull StructuredQuery structuredQuery) {
            this.structuredQuery = structuredQuery;
        }
    }

    class StructuredQuery {
        @SerializedName("from")
        public List<CollectionSelector> from;

        @SerializedName("where")
        public CompositeFilter where;

        @SerializedName("limit")
        public Integer limit;

        public StructuredQuery(@NonNull String collectionId) {
            this.from = java.util.Collections.singletonList(new CollectionSelector(collectionId));
        }
    }

    class CollectionSelector {
        @SerializedName("collectionId")
        public String collectionId;

        public CollectionSelector(@NonNull String collectionId) {
            this.collectionId = collectionId;
        }
    }

    class CompositeFilter {
        @SerializedName("compositeFilter")
        public CompositeFilterBody compositeFilter;

        public CompositeFilter(@NonNull CompositeFilterBody compositeFilter) {
            this.compositeFilter = compositeFilter;
        }
    }

    class CompositeFilterBody {
        @SerializedName("op")
        public String op = "AND";

        @SerializedName("filters")
        public List<FieldFilter> filters;

        public CompositeFilterBody(@NonNull List<FieldFilter> filters) {
            this.filters = filters;
        }
    }

    class FieldFilter {
        @SerializedName("fieldFilter")
        public FieldFilterBody fieldFilter;

        public FieldFilter(@NonNull FieldFilterBody fieldFilter) {
            this.fieldFilter = fieldFilter;
        }
    }

    class FieldFilterBody {
        @SerializedName("field")
        public FieldReference field;

        @SerializedName("op")
        public String op;

        @SerializedName("value")
        public Value value;

        public FieldFilterBody(@NonNull String fieldPath, @NonNull String op,
                               @NonNull Value value) {
            this.field = new FieldReference(fieldPath);
            this.op = op;
            this.value = value;
        }
    }

    class FieldReference {
        @SerializedName("fieldPath")
        public String fieldPath;

        public FieldReference(@NonNull String fieldPath) {
            this.fieldPath = fieldPath;
        }
    }

    /** Resultado de {@code runQuery}: puede venir vacío o sin documento. */
    class QueryResult {
        @SerializedName("document")
        public FirestoreDocument document;

        @SerializedName("readTime")
        public String readTime;
    }

    // ───────────────────────── Constructores de valores ─────────────────────────

    /** Envuelve un texto como valor de Firestore. */
    @NonNull
    static Value ofString(@Nullable String s) {
        Value v = new Value();
        v.stringValue = s != null ? s : "";
        return v;
    }

    /** Envuelve un entero como valor de Firestore (viaja como texto). */
    @NonNull
    static Value ofInteger(long n) {
        Value v = new Value();
        v.integerValue = String.valueOf(n);
        return v;
    }

    /** Envuelve un decimal como valor de Firestore. */
    @NonNull
    static Value ofDouble(double d) {
        Value v = new Value();
        v.doubleValue = d;
        return v;
    }

    /** Envuelve un booleano como valor de Firestore. */
    @NonNull
    static Value ofBoolean(boolean b) {
        Value v = new Value();
        v.booleanValue = b;
        return v;
    }
}
