# The Factory HKA — proveedor DIAN de pruebas

Objetivo: integrar The Factory HKA como proveedor DIAN de sandbox sobre la arquitectura agnóstica, sin romper Siigo ni el mock. Rama `feat/the-factory-hka`. Estrategia de entrega `ask-on-risk` (sin push ni PR por ahora). Runner: `./gradlew test` (JUnit 5 + AssertJ).

## Specs

S1. Adaptadores de Infraestructura: "Crea las clases TheFactoryClient y TheFactoryInvoiceMapper que implementen las interfaces genéricas de facturación. Elimina el código huérfano de los adaptadores de Siigo."

S2. Variables de Entorno: "Define en el .env las nuevas variables requeridas para el sandbox de The Factory (ej. FACTORY_API_TOKEN, FACTORY_TEST_ENV=true, FACTORY_API_URL)."

S3. Script de Sincronización: "Crea el script scripts/factory_sandbox_catalogs.py para consumir la API de pruebas de The Factory y descargar sus códigos oficiales de impuestos y métodos de pago, mapeándolos a nuestro campo provider_external_id."

S4. Pruebas Unitarias: "Construye los tests para verificar que el nuevo TheFactoryInvoiceMapper traduzca correctamente nuestro SalesDocument agnóstico al JSON (o XML) esperado por su API."

S5. Alcance: solo backend; no se solicita trabajo de frontend para esta feature.

## Tasks

- [x] T1 — Configuración: `FactoryProviderProperties`, `application.yaml`, `.env.example`, `.env` local y registro en `PosInventApplication`. Specs: S2. Route: inline. Evidence: `compileJava` OK; 15 claves `FACTORY_*` en `.env.example` y `.env`. Commit: 82a430c.
- [x] T2 — Mapper XML HKA: `TheFactoryInvoiceMapper` con `FacturaGeneral`, totales, impuestos, pagos, cliente y descuentos; tests RED→GREEN. Specs: S1, S4. Route: inline. Evidence: `TheFactoryInvoiceMapperTest` 12/12. Commit: 3f6b62f.
- [x] T3 — Cliente SOAP y adaptador: `TheFactoryClient` (SOAP 1.1 + `SOAPAction`), `TheFactoryProviderAdapter` condicionado a `app.dian.provider=thefactory`, parseo de respuestas/faults y tests. Specs: S1. Route: inline. Evidence: `TheFactoryClientTest` 4/4 + `TheFactoryProviderAdapterTest` 4/4. Commit: b8b0568.
- [x] T4 — Script de catálogos: `scripts/factory_sandbox_catalogs.py` con validación real contra `FoliosRestantes` y mapa oficial DIAN→`provider_external_id`. Specs: S3. Route: inline. Evidence: `--offline` OK; contra sandbox con tokens dummy responde `codigo=101` "El Token Emisor es inválido" y sale con código 2. Commit: 96d59e1.
- [x] T5 — Limpieza y verificación: eliminar código huérfano real, correr suite unitaria completa y revisar el diff. Specs: S1. Route: inline. Evidence: TODOs obsoletos de `ProviderCAdapter` reemplazados; suite unitaria 679/679. Commit: f3d6ff8.


## Log

L1 (solicitud original, verbatim):

> ok, 
> Vamos a integrar el proveedor The Factory HKA para el entorno de pruebas utilizando la arquitectura agnóstica recién unificada:1. Adaptadores de Infraestructura: Crea las clases TheFactoryClient y TheFactoryInvoiceMapper que implementen las interfaces genéricas de facturación. Elimina el código huérfano de los adaptadores de Siigo.2. Variables de Entorno: Define en el .env las nuevas variables requeridas para el sandbox de The Factory (ej. FACTORY_API_TOKEN, FACTORY_TEST_ENV=true, FACTORY_API_URL).3. Script de Sincronización: Crea el script scripts/factory_sandbox_catalogs.py para consumir la API de pruebas de The Factory y descargar sus códigos oficiales de impuestos y métodos de pago, mapeándolos a nuestro campo provider_external_id.4. Pruebas Unitarias: Construye los tests para verificar que el nuevo TheFactoryInvoiceMapper traduzca correctamente nuestro SalesDocument agnóstico al JSON (o XML) esperado por su API. 

L2: Contrato verificado contra el WSDL público `https://demoemision21.thefactoryhka.com.co/ws/v1.0/Service.svc?wsdl`: SOAP 1.1, operaciones `Enviar` (`SOAPAction: http://tempuri.org/IService/Enviar`), `EstadoDocumento`, `FoliosRestantes`. `Enviar` recibe `tokenEmpresa`, `tokenPassword`, `factura` (`FacturaGeneral`) y `adjuntos`. HKA consume códigos DIAN directos: `tipoDocumento=01`, `tipoOperacion=05`, `codigoTOTALImp=01` (IVA), `medioPago=10` (efectivo), `metodoDePago=1`, `tipoIdentificacion=31`, `tipoPersona=1`.

L3: HKA no expone endpoint de catálogo de impuestos/pagos en su WSDL. El script S3 valida conectividad/credenciales con `FoliosRestantes` y emite el mapa oficial DIAN usado por HKA; `provider_external_id = dian_code` para impuestos. La limitación queda documentada en el script y en este log.

L4: El adaptador Siigo no contiene clases ni métodos muertos (verificado por referencias). El único código huérfano real es el bloque de TODOs del mock `ProviderCAdapter`, que referencia un SDK inexistente; se reemplaza por documentación precisa del mock. Siigo sigue funcional detrás de `app.dian.provider=siigo`.

L5: T2 completado. `TheFactoryInvoiceMapper` traduce `ElectronicInvoiceRequest` a `FacturaGeneral` en el namespace `ServiceSoap.UBL2._0.Models.Object`, con totales por línea, descuentos en `cargosDescuentos`, impuestos por línea/generales/totales, medios de pago DIAN y fallback de consumidor final. Parser XML endurecido (DOCTYPE y entidades externas deshabilitadas). `TheFactoryInvoiceMapperTest`: 12/12 en verde.

L6: T3 completado. `TheFactoryClient` hace POST SOAP 1.1 con `SOAPAction` citado y conserva el body de los SOAP Fault para que el mapper los explique. `TheFactoryProviderAdapter` solo se activa con `app.dian.provider=thefactory`, falla rápido si faltan `FACTORY_API_TOKEN`/`FACTORY_API_PASSWORD` y rechaza CUFE en blanco. Tests de cliente 4/4 y adaptador 4/4.

L7: T4 completado. HKA no expone catálogo; el script valida credenciales con `FoliosRestantes` y emite el mapa oficial DIAN (`provider_external_id = dian_code`). Verificación real contra el sandbox: con tokens dummy responde `codigo=101`, `resultado=Error.`, mensaje "El Token Emisor es inválido", y el script sale con código 2. `--offline` emite el mapa sin red y sale 0.

L8: T5 completado. `ProviderCAdapter` ya no tiene TODOs que referencien un SDK inexistente; su javadoc documenta que es el mock por defecto sin validez fiscal. Suite unitaria completa (excluyendo integración Testcontainers por permiso al socket Docker): 679/679, 0 fallos, 0 errores.

L9: Work units commiteados en `feat/the-factory-hka`: 82a430c (config), 3f6b62f (mapper), b8b0568 (cliente/adaptador), 96d59e1 (script), f3d6ff8 (limpieza del mock). Sin push ni PR. `gradlew` queda con un cambio de modo preexistente sin commitear (100644 → 100755), ajeno a esta feature.
