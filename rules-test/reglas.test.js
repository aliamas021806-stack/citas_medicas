// Pruebas de las reglas de Firestore contra el emulador.
// Comprueban lo que de verdad importa: que cada rol sólo alcance lo suyo.
const { initializeTestEnvironment, assertFails, assertSucceeds } = require('@firebase/rules-unit-testing');
const fs = require('fs');

const PACIENTE = 'uid_paciente';
const PACIENTE2 = 'uid_paciente2';
const MEDICO_UID = 'uid_medico';
const MEDICO_FICHA = 'doc_ana';
const ADMIN = 'uid_admin';

let testEnv;

async function seed() {
  // Datos base escritos sin reglas (el emulador con `withSecurityRulesDisabled`).
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await db.doc(`users/${PACIENTE}`).set({ role: 'patient', email: 'paciente@x.com', fullName: 'Paciente Uno' });
    await db.doc(`users/${PACIENTE2}`).set({ role: 'patient', email: 'paciente2@x.com', fullName: 'Paciente Dos' });
    await db.doc(`users/${ADMIN}`).set({ role: 'admin', email: 'admin@x.com', fullName: 'Admin' });

    // Médico YA vinculado: tiene doctorId en el perfil y la ficha tiene su userId.
    // Es el estado real cuando la app ya hizo el vínculo al iniciar sesión, y es
    // lo que permite que su consulta filtrada por doctorId sea autorizable.
    await db.doc(`users/${MEDICO_UID}`).set({
      role: 'patient', email: 'ana@clinica.com', fullName: 'Ana', doctorId: MEDICO_FICHA
    });

    // Invitación pendiente, para probar el paso de reclamar la ficha.
    await db.doc('users/uid_invitado').set({
      role: 'patient', email: 'nuevo@clinica.com', fullName: 'Nuevo'
    });

    // Ficha invitada: tiene el correo del médico pero aún sin userId.
    await db.doc(`doctors/${MEDICO_FICHA}`).set({
      fullName: 'Ana López', specialtyId: 'cardiologia', specialtyName: 'Cardiología',
      email: 'ana@clinica.com', userId: MEDICO_UID, age: 41, yearsExperience: 12,
      consultationFee: 50, rating: 4.8, bio: '', hospital: 'Clínica X', photoUrl: ''
    });

    // Ficha nueva, todavía sin reclamar.
    await db.doc('doctors/doc_nuevo').set({
      fullName: 'Nuevo Médico', specialtyId: 'general', specialtyName: 'General',
      email: 'nuevo@clinica.com', userId: '', age: 35, yearsExperience: 5,
      consultationFee: 30, rating: 0, bio: '', hospital: 'Z', photoUrl: ''
    });
    // Ficha de otro médico, ya reclamada.
    await db.doc('doctors/doc_otro').set({
      fullName: 'Luis Ruiz', specialtyName: 'Pediatría', email: 'luis@clinica.com',
      userId: 'uid_otro', age: 50, yearsExperience: 20, consultationFee: 40,
      rating: 4.5, specialtyId: 'pediatria', bio: '', hospital: 'Y', photoUrl: ''
    });

    // Citas de tres dueños distintos + una del médico.
    await db.doc('appointments/cita1').set({
      ownerId: PACIENTE, doctorId: MEDICO_FICHA, doctorName: 'Ana López',
      specialtyName: 'Cardiología', patientName: 'Paciente Uno', patientAge: 30,
      date: '2025-03-11', time: '10:00', status: 'Confirmed', reason: '', createdAt: 1
    });
    await db.doc('appointments/cita2').set({
      ownerId: PACIENTE2, doctorId: 'doc_otro', doctorName: 'Luis Ruiz',
      specialtyName: 'Pediatría', patientName: 'Paciente Dos', patientAge: 25,
      date: '2025-03-11', time: '11:00', status: 'Confirmed', reason: '', createdAt: 1
    });
  });
}

const as = (uid, email) => testEnv.authenticatedContext(uid, { email }).firestore();
const anon = () => testEnv.unauthenticatedContext().firestore();

const casos = [];
const test = (nombre, fn) => casos.push({ nombre, fn });

// ───────────────────────── Sin sesión ─────────────────────────
test('anónimo NO puede leer doctors', async () => {
  await assertFails(anon().collection('doctors').get());
});
test('anónimo NO puede leer appointments', async () => {
  await assertFails(anon().collection('appointments').get());
});
test('anónimo NO puede escribir appointments', async () => {
  await assertFails(anon().doc('appointments/x').set({ ownerId: 'x' }));
});

// ───────────────────────── Catálogo ─────────────────────────
test('un paciente autenticado SÍ lee doctors', async () => {
  await assertSucceeds(as(PACIENTE, 'paciente@x.com').collection('doctors').get());
});
test('el paciente NO puede dar de alta médicos', async () => {
  await assertFails(as(PACIENTE, 'paciente@x.com').doc('doctors/doc_nuevo').set({
    fullName: 'Intruso', email: 'intruso@x.com', userId: ''
  }));
});

// ───────────────────────── Citas: aislamiento ─────────────────────────
test('el paciente lee SU cita', async () => {
  await assertSucceeds(as(PACIENTE, 'paciente@x.com').doc('appointments/cita1').get());
});
test('el paciente NO lee la cita de otro paciente (el bug reportado)', async () => {
  await assertFails(as(PACIENTE, 'paciente@x.com').doc('appointments/cita2').get());
});
test('el paciente NO puede listar todas las citas', async () => {
  // Una consulta sin filtro por ownerId debe ser rechazada entera.
  await assertFails(as(PACIENTE, 'paciente@x.com').collection('appointments').get());
});
test('el paciente crea SU cita', async () => {
  await assertSucceeds(as(PACIENTE, 'paciente@x.com').doc('appointments/nueva').set({
    ownerId: PACIENTE, doctorId: MEDICO_FICHA, doctorName: 'Ana López',
    specialtyName: 'Cardiología', patientName: 'Paciente Uno', patientAge: 30,
    date: '2025-04-01', time: '09:00', status: 'Confirmed', reason: '', createdAt: 2
  }));
});
test('NO se puede crear una cita a nombre de otra persona', async () => {
  await assertFails(as(PACIENTE, 'paciente@x.com').doc('appointments/falsa').set({
    ownerId: PACIENTE2, doctorId: MEDICO_FICHA, patientName: 'Paciente Dos',
    date: '2025-04-01', time: '10:00', status: 'Confirmed', createdAt: 3
  }));
});
test('el paciente cancela SU cita', async () => {
  await assertSucceeds(as(PACIENTE, 'paciente@x.com').doc('appointments/cita1')
    .update({ status: 'Cancelled' }));
});
test('el paciente NO puede reasignar el médico de su cita', async () => {
  await assertFails(as(PACIENTE, 'paciente@x.com').doc('appointments/cita1')
    .update({ doctorId: 'doc_otro' }));
});

// ───────────────────────── Rol: nadie se autoasciende ─────────────────────────
test('un paciente NO puede ponerse role=admin', async () => {
  await assertFails(as(PACIENTE, 'paciente@x.com').doc(`users/${PACIENTE}`)
    .update({ role: 'admin' }));
});
test('un paciente NO puede escribir doctorId sin ficha que lo respalde', async () => {
  await assertFails(as(PACIENTE, 'paciente@x.com').doc(`users/${PACIENTE}`)
    .update({ doctorId: MEDICO_FICHA }));
});
test('un paciente NO puede apropiarse de la ficha de un médico ya reclamada', async () => {
  await assertFails(as(PACIENTE, 'paciente@x.com').doc('doctors/doc_otro')
    .update({ userId: PACIENTE }));
});

// ───────────────────────── El médico reclama su ficha ─────────────────────────
test('un médico invitado reclama su ficha escribiendo su userId', async () => {
  await assertSucceeds(as('uid_invitado', 'nuevo@clinica.com').doc('doctors/doc_nuevo')
    .update({ userId: 'uid_invitado' }));
});
test('tras reclamar, guarda su doctorId en el perfil', async () => {
  await assertSucceeds(as('uid_invitado', 'nuevo@clinica.com').doc('users/uid_invitado')
    .update({ doctorId: 'doc_nuevo' }));
});
test('el médico NO puede reclamar una ficha con OTRO correo', async () => {
  await assertFails(as(MEDICO_UID, 'ana@clinica.com').doc('doctors/doc_nuevo')
    .update({ userId: MEDICO_UID }));
});
test('el médico ve las citas que le tocan', async () => {
  const snap = await assertSucceeds(
    as(MEDICO_UID, 'ana@clinica.com').collection('appointments')
      .where('doctorId', '==', MEDICO_FICHA).get());
  if (snap.size < 1) throw new Error('El médico debería ver al menos la cita1, vio ' + snap.size);
});
test('el médico NO ve las citas de otro médico', async () => {
  const db = as(MEDICO_UID, 'ana@clinica.com');
  await assertFails(db.collection('appointments').where('doctorId', '==', 'doc_otro').get());
});
test('el médico NO puede cambiar la fecha de una cita', async () => {
  await assertFails(as(MEDICO_UID, 'ana@clinica.com').doc('appointments/cita1')
    .update({ date: '2030-01-01' }));
});

// ───────────────────────── El administrador ─────────────────────────
test('el admin SÍ da de alta médicos', async () => {
  await assertSucceeds(as(ADMIN, 'admin@x.com').doc('doctors/doc_nuevo').set({
    fullName: 'Nuevo Médico', specialtyId: 'general', specialtyName: 'General',
    email: 'nuevo@x.com', userId: '', age: 35, yearsExperience: 5,
    consultationFee: 30, rating: 0, bio: '', hospital: 'Z', photoUrl: ''
  }));
});
test('el admin cambia el rol de otra persona (nombrar médico)', async () => {
  await assertSucceeds(as(ADMIN, 'admin@x.com').doc(`users/${PACIENTE2}`)
    .update({ role: 'doctor', doctorId: 'doc_nuevo' }));
});
test('el admin ve todas las citas', async () => {
  const snap = await assertSucceeds(as(ADMIN, 'admin@x.com').collection('appointments').get());
  if (snap.size < 2) throw new Error('El admin debería ver todas las citas, vio ' + snap.size);
});

// ───────────────────────── Expedientes ─────────────────────────
test('el paciente escribe SU expediente', async () => {
  await assertSucceeds(as(PACIENTE, 'paciente@x.com').doc(`patients/${PACIENTE}`).set({
    fullName: 'Paciente Uno', phone: '600111222', bloodType: 'O+'
  }));
});
test('el paciente NO escribe el expediente de otro', async () => {
  await assertFails(as(PACIENTE, 'paciente@x.com').doc(`patients/${PACIENTE2}`).set({
    fullName: 'Suplantación'
  }));
});

// ───────────────────────── Ejecución ─────────────────────────
(async () => {
  testEnv = await initializeTestEnvironment({
    projectId: 'citas-medicas-e19ca',
    firestore: { rules: fs.readFileSync('firestore.rules', 'utf8'), host: '127.0.0.1', port: 8089 }
  });

  let ok = 0;
  const fallos = [];
  for (const c of casos) {
    await testEnv.clearFirestore();
    await seed();
    try {
      await c.fn();
      console.log('  ✅ ' + c.nombre);
      ok++;
    } catch (e) {
      console.log('  ❌ ' + c.nombre + '\n       → ' + (e.message || e).toString().split('\n')[0]);
      fallos.push(c.nombre);
    }
  }

  console.log(`\nRESULTADO: ${ok}/${casos.length} pruebas de reglas correctas`);
  if (fallos.length) {
    console.log('FALLARON:\n - ' + fallos.join('\n - '));
  }
  await testEnv.cleanup();
  process.exit(fallos.length ? 1 : 0);
})();
