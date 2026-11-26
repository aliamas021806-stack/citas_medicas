# Pruebas de las reglas de Firestore

Comprueban, contra el emulador oficial de Firebase, que cada rol alcanza **sólo** lo
suyo. No necesitan conexión con el proyecto real ni credenciales.

```bash
cd rules-test
npm install
npm test
```

Resultado esperado: **26/26 pruebas correctas**.

Cubren los casos que más caro salen si fallan:

- Sin sesión no se lee nada (403).
- **Un paciente no ve la cita de otro paciente** — el fallo reportado en la v5.0.
- Un paciente no puede listar todas las citas ni crear una a nombre de otra persona.
- Un paciente **no puede autoascenderse** a admin ni escribir su `doctorId`.
- Un paciente **no puede apropiarse** de la ficha de un médico ya reclamada.
- El médico reclama su ficha sólo si el correo coincide con el del token.
- El médico ve sus citas y **no** las de otro médico.
- El admin da de alta médicos, cambia roles y ve todas las citas.

> El test lee `../firestore.rules`, así que si cambias las reglas se comprueba
> automáticamente la versión nueva: no hay copia que se pueda quedar desactualizada.
