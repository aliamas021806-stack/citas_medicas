// Pruebas de las tres preguntas concretas del usuario:
//   1. ¿El médico recupera de Firestore SOLO las citas que le corresponden a él?
//   2. ¿Están separados los usuarios médico de los pacientes?
//   3. ¿Puede un médico crear su propia ficha al registrarse?
//
// Se ejecutan contra el emulador de Firestore con las reglas reales de
// ../firestore.rules (copiadas por el script `pretest`).
// Mismo runner que reglas.test.js: no hace falta mocha ni jest.
const { initializeTestEnvironment, assertFails, assertSucceeds } = require('@firebase/rules-unit-testing');
const fs = require('fs');

const PROYECTO = 'citas-medicas-e19ca';

// Dos médicos distintos, cada uno con su ficha y su cuenta, para comprobar el
// aislamiento: el fallo caro sería que uno viera las citas del otro.
const MEDICO_A_UID = 'uid_medico_a';
const MEDICO_A_FICHA = 'doc_ana';
const MEDICO_B_UID = 'uid_medico_b';
const MEDICO_B_FICHA = 'doc_luis';
const PACIENTE = 'uid_paciente';
const ADMIN = 'uid_admin';

let testEnv;

const as = (uid, email) => testEnv.authenticatedContext(uid, { email }).firestore();

async function seed() {
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();

    // ── Separación médico/paciente: el rol no está en el token, sino en
    // /users/{uid} (admin) y en doctors.userId (médico).
    await db.doc(`users/${MEDICO_A_UID}`).set({
      role: 'patient', email: 'ana@clinica.com', fullName: 'Ana López',
      doctorId: MEDICO_A_FICHA,
    });
    await db.doc(`users/${MEDICO_B_UID}`).set({
      role: 'patient', email: 'luis@clinica.com', fullName: 'Luis Ruiz',
      doctorId: MEDICO_B_FICHA,
    });
    await db.doc(`users/${PACIENTE}`).set({
      role: 'patient', email: 'paciente@x.com', fullName: 'Paciente Uno',
    });
    await db.doc(`users/${ADMIN}`).set({
      role: 'admin', email: 'admin@x.com', fullName: 'Admin',
    });

    await db.doc(`doctors/${MEDICO_A_FICHA}`).set({
      fullName: 'Ana López', specialtyId: 'cardiologia', specialtyName: 'Cardiología',
      email: 'ana@clinica.com', userId: MEDICO_A_UID,
    });
    await db.doc(`doctors/${MEDICO_B_FICHA}`).set({
      fullName: 'Luis Ruiz', specialtyId: 'pediatria', specialtyName: 'Pediatría',
      email: 'luis@clinica.com', userId: MEDICO_B_UID,
    });

    // ── Una cita de cada médico, el mismo día: así se comprueba que cada uno ve la suya.
    await db.doc('appointments/cita_de_ana').set({
      ownerId: PACIENTE, doctorId: MEDICO_A_FICHA, doctorName: 'Ana López',
      specialtyName: 'Cardiología', patientName: 'Paciente Uno', patientAge: 30,
      date: '2026-11-27', time: '12:00', status: 'Confirmed', reason: '', createdAt: 1,
    });
    await db.doc('appointments/cita_de_luis').set({
      ownerId: PACIENTE, doctorId: MEDICO_B_FICHA, doctorName: 'Luis Ruiz',
      specialtyName: 'Pediatría', patientName: 'Paciente Uno', patientAge: 30,
      date: '2026-11-27', time: '09:00', status: 'Confirmed', reason: '', createdAt: 2,
    });
  });
}

const casos = [];
const test = (nombre, fn) => casos.push({ nombre, fn });

// ═══════════════════════════════════════════════════════════════════════
// 1. ¿El médico ve SOLO sus citas, y las recupera de Firestore?
// ═══════════════════════════════════════════════════════════════════════

// Reproduce exactamente la consulta de FirestoreDoctorAgendaRepository.cargarAgenda().
test('1. la consulta por su doctorId devuelve SÓLO sus citas', async () => {
  const snap = await assertSucceeds(
    as(MEDICO_A_UID, 'ana@clinica.com')
      .collection('appointments')
      .where('doctorId', '==', MEDICO_A_FICHA)
      .get()
  );
  if (snap.size !== 1) throw new Error(`esperaba 1 cita, llegaron ${snap.size}`);
  if (snap.docs[0].id !== 'cita_de_ana') throw new Error('devolvió la cita equivocada');
});

test('1. el otro médico ve la suya y NO la del primero', async () => {
  const snap = await assertSucceeds(
    as(MEDICO_B_UID, 'luis@clinica.com')
      .collection('appointments')
      .where('doctorId', '==', MEDICO_B_FICHA)
      .get()
  );
  if (snap.size !== 1) throw new Error(`esperaba 1 cita, llegaron ${snap.size}`);
  if (snap.docs[0].id !== 'cita_de_luis') throw new Error('devolvió la cita equivocada');
});

// Un médico que intenta consultar la agenda de OTRO no recibe una lista vacía:
// la consulta se rechaza entera con permission-denied. Es más seguro así, porque
// una lista vacía se confundiría con "este médico no tiene citas".
test('1. un médico NO puede consultar la agenda de otro médico', async () => {
  await assertFails(
    as(MEDICO_A_UID, 'ana@clinica.com')
      .collection('appointments')
      .where('doctorId', '==', MEDICO_B_FICHA)
      .get()
  );
});

test('1. un médico NO puede listar todas las citas sin filtrar', async () => {
  await assertFails(as(MEDICO_A_UID, 'ana@clinica.com').collection('appointments').get());
});

test('1. el médico lee el documento de su cita', async () => {
  await assertSucceeds(
    as(MEDICO_A_UID, 'ana@clinica.com').doc('appointments/cita_de_ana').get()
  );
});

test('1. el médico NO lee el documento de una cita ajena', async () => {
  await assertFails(
    as(MEDICO_A_UID, 'ana@clinica.com').doc('appointments/cita_de_luis').get()
  );
});

test('1. el médico marca como atendida su cita (status = Attended)', async () => {
  await assertSucceeds(
    as(MEDICO_A_UID, 'ana@clinica.com')
      .doc('appointments/cita_de_ana')
      .update({ status: 'Attended' })
  );
});

test('1. el médico marca su cita como NoShow', async () => {
  await assertSucceeds(
    as(MEDICO_A_UID, 'ana@clinica.com')
      .doc('appointments/cita_de_ana')
      .update({ status: 'NoShow' })
  );
});

test('1. el médico NO cambia el estado de una cita ajena', async () => {
  await assertFails(
    as(MEDICO_A_UID, 'ana@clinica.com')
      .doc('appointments/cita_de_luis')
      .update({ status: 'Attended' })
  );
});

// El PATCH de la app manda SÓLO status. Si las reglas permitiesen más campos, un
// médico podría quedarse con una cita ajena o falsear la fecha.
test('1. el médico NO reasigna la cita a otro médico', async () => {
  await assertFails(
    as(MEDICO_A_UID, 'ana@clinica.com')
      .doc('appointments/cita_de_ana')
      .update({ doctorId: MEDICO_B_FICHA })
  );
});

test('1. el médico NO cambia la fecha de su cita', async () => {
  await assertFails(
    as(MEDICO_A_UID, 'ana@clinica.com')
      .doc('appointments/cita_de_ana')
      .update({ date: '2026-12-01' })
  );
});

// ═══════════════════════════════════════════════════════════════════════
// 2. ¿Están separados los médicos de los pacientes?
// ═══════════════════════════════════════════════════════════════════════

test('2. el paciente ve SÓLO sus propias citas', async () => {
  const snap = await assertSucceeds(
    as(PACIENTE, 'paciente@x.com')
      .collection('appointments')
      .where('ownerId', '==', PACIENTE)
      .get()
  );
  // Las dos citas las reservó él: las ve como paciente, no como agenda médica.
  if (snap.size !== 2) throw new Error(`esperaba 2, llegaron ${snap.size}`);
});

test('2. un paciente NO accede a la agenda filtrada por doctorId', async () => {
  await assertFails(
    as(PACIENTE, 'paciente@x.com')
      .collection('appointments')
      .where('doctorId', '==', MEDICO_A_FICHA)
      .get()
  );
});

test('2. un paciente NO se autoasciende escribiendo doctorId en su perfil', async () => {
  await assertFails(
    as(PACIENTE, 'paciente@x.com')
      .doc(`users/${PACIENTE}`)
      .update({ doctorId: MEDICO_A_FICHA })
  );
});

test('2. un paciente NO se apropia de una ficha ya reclamada', async () => {
  await assertFails(
    as(PACIENTE, 'paciente@x.com')
      .doc(`doctors/${MEDICO_A_FICHA}`)
      .update({ userId: PACIENTE })
  );
});

test('2. un paciente NO crea fichas en doctors', async () => {
  await assertFails(
    as(PACIENTE, 'paciente@x.com').doc('doctors/doc_intruso').set({
      fullName: 'Intruso', email: 'paciente@x.com', userId: PACIENTE,
    })
  );
});

test('2. el administrador SÍ crea fichas de médico', async () => {
  await assertSucceeds(
    as(ADMIN, 'admin@x.com').doc('doctors/doc_nuevo_admin').set({
      fullName: 'Nuevo', email: 'nuevo@clinica.com', userId: '',
    })
  );
});

test('2. el administrador ve todas las citas', async () => {
  const snap = await assertSucceeds(
    as(ADMIN, 'admin@x.com').collection('appointments').get()
  );
  if (snap.size !== 2) throw new Error(`esperaba 2, llegaron ${snap.size}`);
});

// ═══════════════════════════════════════════════════════════════════════
// 3. Alta del médico: la ficha la crea un administrador, NO el propio médico
//
// Se probó a dejar que el médico creara su propia ficha al registrarse. Se
// descartó y estas pruebas documentan por qué: como la API key es pública por
// diseño, cualquiera podía crearse una ficha con su propio correo y su propio uid
// y dejar de ser paciente. Eso rompe justo la separación médico/paciente.
// ═══════════════════════════════════════════════════════════════════════

test('3. un médico recién registrado NO puede crear su propia ficha', async () => {
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    await ctx.firestore().doc('users/uid_medico_nuevo').set({
      role: 'patient', email: 'nuevo@clinica.com', fullName: 'Médico Nuevo',
    });
  });
  await assertFails(
    as('uid_medico_nuevo', 'nuevo@clinica.com').doc('doctors/doc_nuevo').set({
      fullName: 'Médico Nuevo',
      specialtyId: 'general',
      specialtyName: 'Medicina General',
      email: 'nuevo@clinica.com',
      userId: 'uid_medico_nuevo',
    })
  );
});

test('3. el administrador da de alta su ficha y él la vincula al entrar', async () => {
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    await ctx.firestore().doc('users/uid_medico_nuevo').set({
      role: 'patient', email: 'nuevo@clinica.com', fullName: 'Médico Nuevo',
    });
  });
  // 1) El administrador crea la ficha con el correo del médico (invitación).
  await assertSucceeds(
    as(ADMIN, 'admin@x.com').doc('doctors/doc_nuevo').set({
      fullName: 'Médico Nuevo', email: 'nuevo@clinica.com', userId: '',
    })
  );
  // 2) El médico, al registrarse con ese mismo correo, reclama su ficha.
  await assertSucceeds(
    as('uid_medico_nuevo', 'nuevo@clinica.com')
      .doc('doctors/doc_nuevo')
      .update({ userId: 'uid_medico_nuevo' })
  );
  // 3) Y escribe su doctorId en su perfil, que es lo que autoriza su agenda.
  await assertSucceeds(
    as('uid_medico_nuevo', 'nuevo@clinica.com')
      .doc('users/uid_medico_nuevo')
      .update({ doctorId: 'doc_nuevo' })
  );
});

// El límite de seguridad: no puede crear una ficha con el correo de otra persona.
test('3. NO crea una ficha con el correo de otra persona', async () => {
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    await ctx.firestore().doc('users/uid_medico_nuevo').set({
      role: 'patient', email: 'nuevo@clinica.com', fullName: 'Médico Nuevo',
    });
  });
  await assertFails(
    as('uid_medico_nuevo', 'nuevo@clinica.com').doc('doctors/doc_robo').set({
      fullName: 'Robado', email: 'ana@clinica.com', userId: 'uid_medico_nuevo',
    })
  );
});

test('3. NO se queda con la ficha de un médico ya vinculado', async () => {
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    await ctx.firestore().doc('users/uid_intruso').set({
      role: 'patient', email: 'intruso@x.com', fullName: 'Intruso',
    });
  });
  await assertFails(
    as('uid_intruso', 'intruso@x.com')
      .doc(`doctors/${MEDICO_A_FICHA}`)
      .update({ userId: 'uid_intruso' })
  );
});

// ═══════════════════════════════════════════════════════════════════════
// Runner
// ═══════════════════════════════════════════════════════════════════════

(async () => {
  testEnv = await initializeTestEnvironment({
    projectId: PROYECTO,
    firestore: { rules: fs.readFileSync('firestore.rules', 'utf8'), host: '127.0.0.1', port: 8089 },
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
