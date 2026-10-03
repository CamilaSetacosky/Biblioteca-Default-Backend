# Documento de Medición de Puntos de Función Ajustados (PFA)
## Sistema de Gestión de Biblioteca (Biblioteca API)

---

## 1. Información del Documento y Control de Versión

| Atributo | Detalle |
| :--- | :--- |
| **Nombre del documento** | Medición de Puntos de Función Ajustados (`medicionPFA.md`) |
| **Proyecto / Sistema** | Sistema de Gestión de Biblioteca (`Biblioteca API`) |
| **Autor / Analista** | Antigravity AI - Advanced Agentic Coding Pair Programmer |
| **Estándar Metodológico** | IFPUG Counting Practices Manual (CPM) Release 4.2.1 |
| **Tipo de Conteo** | Conteo de Aplicación / Línea Base (Application Function Point Count) |
| **Fuentes de Entrada** | `FEATURE-SET.md`, `docs/DATABASE_DATA_MODEL.md`, `docs/SETUP_AND_TROUBLESHOOTING.md`, código fuente Java Spring Boot (`src/main/java/`) |
| **Estado** | Aprobado / Finalizado para Línea Base de Tamaño Funcional |
| **Fecha de Elaboración** | 28 de septiembre de 2026 |

---

## 2. Marco Metodológico y Criterios del Estándar IFPUG CPM 4.2.1

La presente medición se fundamenta estrictamente en los lineamientos normativos del **Manual de Prácticas de Conteo de IFPUG (Counting Practices Manual - CPM Release 4.2.1)**. A continuación se resumen los conceptos, definiciones, reglas de conteo y matrices de complejidad aplicadas en este análisis.

### 2.1. Frontera de la Aplicación (Application Boundary)
La frontera de la aplicación delimita el software objeto de medición del universo exterior (usuarios, clientes HTTP, aplicaciones satélites y servicios externos):
* **Dentro de la Frontera:** El backend Spring Boot, la lógica de negocio, las capas de servicios, la persistencia en base de datos relacional (tablas locales), la máquina de estados de préstamos, las rutinas automáticas programadas (Scheduler) y los escuchadores reactivos de eventos (`TransactionalEventListener`).
* **Fuera de la Frontera:**
  1. **Usuarios y Clientes HTTP:** Navegadores, clientes móviles o herramientas que consumen la API REST mediante roles `SOCIO`, `BIBLIOTECARIO` o visitante no autenticado.
  2. **Google Books API:** Proveedor de servicios web externo que almacena catálogos bibliográficos mundiales, consumido como fuente de datos externa.
  3. **Servidor SMTP (Email Service):** Servidor de correo externo que recibe las peticiones de despacho generadas por el sistema.

```
       +-----------------------------------------------------------------------+
       |               USUARIOS / CLIENTES REST (Socio, Bibliotecario)          |
       +-----------------------------------------------------------------------+
                                           | HTTP/JSON (Endpoints REST)
                                           v
+=====================================================================================+
|  FRONTERA DE LA APLICACIÓN: BIBLIOTECA API (Spring Boot Backend)                    |
|                                                                                     |
|   +-----------------------------------------------------------------------------+   |
|   | CONTROLADORES Y ENDPOINTS REST (34 Endpoints)                               |   |
|   | Auth, UserProfile, UserAdmin, Author, Category, Book, BookSync, Loan        |   |
|   +-----------------------------------------------------------------------------+   |
|                                          |                                          |
|   +-----------------------------------------------------------------------------+   |
|   | LÓGICA DE NEGOCIO Y SERVICIOS CORE                                          |   |
|   | Validaciones de negocio, cálculo de plazos, atómica de inventario           |   |
|   +-----------------------------------------------------------------------------+   |
|         |                                 |                                 |       |
|         v                                 v                                 v       |
|   +-------------------+          +-------------------+          +---------------+   |
|   | ARCHIVOS LÓGICOS  |          | EVENT LISTENERS   |          | SCHEDULERS    |   |
|   | INTERNOS (ILF)    |          | Notif. Préstamo   |          | Barrido diario|   |
|   | BD Relacional     |          | (Comprobante HTML)|          | (Recordatorio)|   |
|   +-------------------+          +-------------------+          +---------------+   |
+==========================================|==================================|=======+
                                           |                                  |
                                           v SMTP                             v WebClient
                           +-------------------------------+  +-------------------------------+
                           | SERVIDOR SMTP EXTERNO (Email) |  | GOOGLE BOOKS API (EIF)        |
                           +-------------------------------+  +-------------------------------+
```

---

### 2.2. Tipología de Funciones de Datos (Data Functions)

Las funciones de datos representan los requerimientos de almacenamiento y persistencia lógica del usuario:

1. **Archivos Lógicos Internos (ILF / ALI - Internal Logical Files):**
   * Grupos de datos lógicamente relacionados o información de control identificable por el usuario, mantenidos **dentro** de la frontera de la aplicación mediante procesos elementales de la misma.
2. **Archivos de Interfaz Externa (EIF / AIE - External Interface Files):**
   * Grupos de datos lógicamente relacionados o información de control referenciados por la aplicación, pero mantenidos **fuera** de la frontera de la aplicación por otro sistema (en este proyecto: el catálogo de Google Books).

#### Reglas de Complejidad para ILF y EIF
La complejidad se determina cruzando el número de **Tipos de Elementos de Registro (RET - Record Element Types)** y el número de **Tipos de Elementos de Datos (DET - Data Element Types)**:

| Número de RETs | 1 a 19 DETs | 20 a 50 DETs | 51 o más DETs |
| :---: | :---: | :---: | :---: |
| **1 RET** | Baja (Low) | Baja (Low) | Media (Average) |
| **2 a 5 RETs** | Baja (Low) | Media (Average) | Alta (High) |
| **6 o más RETs** | Media (Average) | Alta (High) | Alta (High) |

#### Ponderación en Puntos de Función para Funciones de Datos

| Tipo de Componente | Complejidad Baja | Complejidad Media | Complejidad Alta |
| :--- | :---: | :---: | :---: |
| **ILF (Archivo Lógico Interno)** | **7 PF** | **10 PF** | **15 PF** |
| **EIF (Archivo de Interfaz Externa)** | **5 PF** | **7 PF** | **10 PF** |

---

### 2.3. Tipología de Funciones Transaccionales (Transactional Functions)

Representan la funcionalidad provista al usuario para procesar datos (entrada, salida y recuperación):

1. **Entrada Externa (EI / EE - External Input):**
   * Proceso elemental que procesa datos o información de control proveniente del exterior de la frontera. Su intención primaria es mantener uno o más ILFs y/o alterar el comportamiento del sistema.
   * **Criterio Fundamental de Proceso Elemental en ABMs:** El estándar IFPUG dictamina explícitamente que cada operación individual de mantenimiento sobre una entidad (Alta, Modificación, Baja) constituye un proceso elemental diferenciado con distinta lógica de procesamiento y diferente conjunto de datos de entrada/salida. Por lo tanto, un ABM no se cuenta como una transacción única, sino como **tres Entradas Externas independientes**:
     1. Alta (Creación / Inserción).
     2. Modificación (Actualización total o parcial).
     3. Baja (Eliminación lógica o física).
2. **Salida Externa (EO / SE - External Output):**
   * Proceso elemental que envía datos o información de control al exterior de la frontera. Su intención primaria es presentar información a través de lógica de procesamiento que **contiene al menos una fórmula matemática, cálculo o creación de datos derivados**, o mantiene al menos un ILF, o altera el comportamiento del sistema.
3. **Consulta Externa (EQ / CE - External Inquiry):**
   * Proceso elemental que envía datos o información de control al exterior de la frontera. Su intención primaria es presentar información mediante la simple recuperación de datos desde un ILF o EIF. Su lógica de procesamiento **no contiene fórmulas matemáticas, no genera datos derivados, ni mantiene ILFs**.

#### Matrices de Complejidad para Transacciones

##### Matriz para Entradas Externas (EI)
Cruzando **Archivos Tipos Referenciados (FTR - File Types Referenced)** y **Elementos de Datos (DET)**:

| Número de FTRs | 1 a 4 DETs | 5 a 15 DETs | 16 o más DETs |
| :---: | :---: | :---: | :---: |
| **0 a 1 FTR** | Baja (Low) | Baja (Low) | Media (Average) |
| **2 FTRs** | Baja (Low) | Media (Average) | Alta (High) |
| **3 o más FTRs** | Media (Average) | Alta (High) | Alta (High) |

##### Matriz para Salidas Externas (EO) y Consultas Externas (EQ)

| Tipo de Transacción | Número de FTRs | 1 a 5 DETs | 6 a 19 DETs | 20 o más DETs |
| :--- | :---: | :---: | :---: | :---: |
| **EO / EQ** | **0 a 1 FTR** | Baja (Low) | Baja (Low) | Media (Average) |
| **EO / EQ** | **2 a 3 FTRs** | Baja (Low) | Media (Average) | Alta (High) |
| **EO / EQ** | **4 o más FTRs** | Media (Average) | Alta (High) | Alta (High) |

#### Ponderación en Puntos de Función para Transacciones

| Tipo de Transacción | Complejidad Baja | Complejidad Media | Complejidad Alta |
| :--- | :---: | :---: | :---: |
| **EI (Entrada Externa)** | **3 PF** | **4 PF** | **6 PF** |
| **EO (Salida Externa)** | **4 PF** | **5 PF** | **7 PF** |
| **EQ (Consulta Externa)** | **3 PF** | **4 PF** | **6 PF** |

---

### 2.4. Factor de Ajuste del Valor (VAF / FA)

El Factor de Ajuste evalúa el impacto de 14 Características Generales del Sistema (GSC - General System Characteristics) en una escala de 0 (sin influencia) a 5 (influencia fuerte):

$$TDI = \sum_{i=1}^{14} C_i \quad \text{(Total Degree of Influence)}$$

$$VAF = (TDI \times 0.01) + 0.65 \quad \text{(Value Adjustment Factor)}$$

$$\text{Puntos de Función Ajustados (AFP)} = \text{Puntos de Función No Ajustados (UFP)} \times VAF$$

---

## 3. Inventario Exhaustivo de Endpoints y Procesos del Proyecto

El sistema expone **34 endpoints REST** distribuidos en 8 controladores Spring Boot, sumados a **2 procesos automatizados en segundo plano** (1 listener transaccional de eventos y 1 scheduler de ejecución periódica por lotes), totalizando **36 procesos elementales transaccionales**:

| Nº | Controlador / Componente | Método HTTP | Ruta de la API / Disparador | Roles / Permisos | Descripción Funcional |
| :---: | :--- | :---: | :--- | :--- | :--- |
| **1** | `CategoryController` | `POST` | `/api/categorias` | `BIBLIOTECARIO` | Alta de nueva categoría taxonómica |
| **2** | `CategoryController` | `PUT` | `/api/categorias/{publicId}` | `BIBLIOTECARIO` | Modificación de categoría existente |
| **3** | `CategoryController` | `DELETE` | `/api/categorias/{publicId}` | `BIBLIOTECARIO` | Baja/eliminación de categoría sin libros vinculados |
| **4** | `CategoryController` | `GET` | `/api/categorias` | `SOCIO`, `BIBLIOTECARIO` | Consulta paginada del catálogo de categorías |
| **5** | `CategoryController` | `GET` | `/api/categorias/{publicId}` | `SOCIO`, `BIBLIOTECARIO` | Consulta de categoría por su UUID público |
| **6** | `AuthorController` | `POST` | `/api/autores` | `BIBLIOTECARIO` | Alta de nuevo autor |
| **7** | `AuthorController` | `PUT` | `/api/autores/{publicId}` | `BIBLIOTECARIO` | Modificación de autor existente |
| **8** | `AuthorController` | `DELETE` | `/api/autores/{publicId}` | `BIBLIOTECARIO` | Baja/eliminación de autor sin libros asociados |
| **9** | `AuthorController` | `GET` | `/api/autores` | `SOCIO`, `BIBLIOTECARIO` | Búsqueda y filtrado de autores por texto/nacionalidad |
| **10** | `AuthorController` | `GET` | `/api/autores/{publicId}` | `SOCIO`, `BIBLIOTECARIO` | Consulta de autor por su UUID público |
| **11** | `BookController` | `POST` | `/api/v1/books` | `BIBLIOTECARIO` | Alta manual de libro en el catálogo con stock inicial |
| **12** | `BookController` | `PUT` | `/api/v1/books/{isbn}` | `BIBLIOTECARIO` | Actualización integral de metadatos y stock de libro |
| **13** | `BookController` | `PATCH` | `/api/v1/books/{isbn}` | `BIBLIOTECARIO` | Modificación parcial de metadatos del libro (Patch) |
| **14** | `BookController` | `PATCH` | `/api/v1/books/{isbn}/portada`| `BIBLIOTECARIO` | Actualización de la URL de la portada del libro |
| **15** | `BookController` | `PATCH` | `/api/v1/books/{isbn}/stock`  | `BIBLIOTECARIO` | Ajuste físico atómico de inventario de un libro |
| **16** | `BookController` | `GET` | `/api/v1/books` | `SOCIO`, `BIBLIOTECARIO` | Búsqueda multicriterio con filtros dinámicos y paginación |
| **17** | `BookController` | `GET` | `/api/v1/books/{isbn}` | `SOCIO`, `BIBLIOTECARIO` | Consulta de ficha bibliográfica de libro por ISBN |
| **18** | `BookController` | `POST` | `/api/v1/books/import` | `BIBLIOTECARIO` | Ingesta e importación directa desde Google Books |
| **19** | `BookController` | `GET` | `/api/v1/books/external/isbn/{isbn}` | `SOCIO`, `BIBLIOTECARIO` | Previsualización de borrador externo (Pre-fill) |
| **20** | `BookSyncController` | `POST` | `/api/v1/books/{identifier}/sync-google-books` | `BIBLIOTECARIO` | Sincronización no destructiva con Google Books |
| **21** | `AuthController` | `POST` | `/api/v1/auth/register` | Público (Visitante) | Registro de nuevo socio con cuenta inactiva |
| **22** | `AuthController` | `POST` | `/api/v1/auth/confirm-account`| Público (Visitante) | Activación de cuenta mediante token temporal de email |
| **23** | `AuthController` | `POST` | `/api/v1/auth/resend-verification` | Público (Visitante) | Reenvío de token de confirmación de cuenta |
| **24** | `AuthController` | `POST` | `/api/v1/auth/forgot-password` | Público (Visitante) | Solicitud de reseteo de clave con protección anti-enumeración |
| **25** | `AuthController` | `POST` | `/api/v1/auth/reset-password` | Público (Visitante) | Reestablecimiento de contraseña mediante token |
| **26** | `AuthController` | `POST` | `/api/v1/auth/login` | Público (Cualquiera) | Autenticación de credenciales y emisión de JWT/Refresh |
| **27** | `AuthController` | `POST` | `/api/v1/auth/refresh` | Público (Cualquiera) | Renovación y rotación de sesión vía Refresh Token |
| **28** | `UserProfileController`| `GET` | `/api/usuarios/me` | Autenticado (`Self`) | Consulta de datos del perfil personal en sesión |
| **29** | `UserProfileController`| `GET` | `/api/usuarios/me/prestamos` | Autenticado (`Self`) | Consulta paginada del historial propio de préstamos |
| **30** | `UserAdminController` | `PATCH`| `/api/usuarios/{publicId}/estado` | `BIBLIOTECARIO` | Habilitación o inhabilitación administrativa de usuario |
| **31** | `UserAdminController` | `PATCH`| `/api/usuarios/{publicId}/rol` | `BIBLIOTECARIO` | Modificación de rol administrativo del usuario |
| **32** | `LoanController` | `POST` | `/api/prestamos` | `SOCIO`, `BIBLIOTECARIO` | Solicitud y concesión transaccional de préstamo |
| **33** | `LoanController` | `PUT` | `/api/prestamos/{publicId}/devolucion` | `BIBLIOTECARIO` | Asentamiento de devolución y recálculo de puntualidad |
| **34** | `LoanController` | `GET` | `/api/prestamos/supervision` | `BIBLIOTECARIO` | Auditoría de préstamos con cálculo dinámico de retraso |
| **35** | `LoanNotificationListener`| `Evento` | `LoanCreatedEvent` (`AFTER_COMMIT`) | Proceso Interno | Emisión de comprobante digital HTML por email |
| **36** | `LoanReminderScheduler`| `Cron` | `0 0 8 * * *` (Diario 08:00 AM) | Proceso Interno | Barrido diario y envío de alertas de vencimiento 48hs |

---

## 4. Medición de Funciones de Datos (Data Functions)

De acuerdo con las reglas de IFPUG CPM 4.2.1, los grupos lógicos de datos deben ser analizados desde la perspectiva del usuario y no meramente como tablas físicas de implementación normalizada. Analizando el modelo entidad-relación (`docs/DATABASE_DATA_MODEL.md`), se identifican **5 Archivos Lógicos Internos (ILF)** y **1 Archivo de Interfaz Externa (EIF)**.

### 4.1. Análisis Detallado de Cada Archivo de Datos

#### ILF-01: Usuarios, Credenciales y Seguridad
* **Tablas físicas agrupadas:** `users`, `credentials`, `roles`, `permits`, `credentials_roles`, `role_permits`, `account_verification_tokens`, `password_reset_tokens`.
* **Justificación de Agrupación:** Representa la totalidad de la entidad "Usuario de la Biblioteca" y sus atributos de seguridad, roles, permisos y tokens temporales de ciclo de vida. No constituyen entidades de negocio autónomas desde el punto de vista del usuario.
* **Tipos de Elementos de Registro (RET): 3 RETs**
  1. `RET 1: Datos de Perfil del Usuario` (tabla `users`).
  2. `RET 2: Credenciales de Acceso y Roles` (tablas `credentials`, `roles`, `permits`).
  3. `RET 3: Tokens de Autenticación y Seguridad` (tablas `account_verification_tokens`, `password_reset_tokens`).
* **Tipos de Elementos de Datos (DET): 16 DETs**
  1. `publicId` (identificador público UUID)
  2. `nombre`
  3. `apellido`
  4. `email`
  5. `dni`
  6. `telefono`
  7. `activo` (estado de negocio)
  8. `enabled` (estado de seguridad)
  9. `rol` (rol visible)
  10. `username`
  11. `password` (hash BCrypt)
  12. `refresh_token`
  13. `verification_token`
  14. `reset_token`
  15. `expiry_date` (vigencia de token)
  16. `token_used` (indicador de consumo único)
* **Evaluación de Complejidad:** 3 RETs y 16 DETs $\implies$ **Complejidad Baja (Low) = 7 PF**.

#### ILF-02: Categorías de Libros
* **Tabla física:** `categories`.
* **Justificación:** Catálogo maestro que administra las temáticas y clasificaciones del acervo bibliográfico.
* **Tipos de Elementos de Registro (RET): 1 RET**
  1. `RET 1: Categoría`.
* **Tipos de Elementos de Datos (DET): 3 DETs**
  1. `publicId` (UUID público)
  2. `nombre` (denominación única de la categoría)
  3. `descripcion` (alcance temático)
* **Evaluación de Complejidad:** 1 RET y 3 DETs $\implies$ **Complejidad Baja (Low) = 7 PF**.

#### ILF-03: Autores de Obras
* **Tabla física:** `authors`.
* **Justificación:** Catálogo maestro que almacena los datos biográficos de los creadores de las obras.
* **Tipos de Elementos de Registro (RET): 1 RET**
  1. `RET 1: Autor`.
* **Tipos de Elementos de Datos (DET): 5 DETs**
  1. `publicId` (UUID público)
  2. `nombre`
  3. `apellido`
  4. `nacionalidad`
  5. `fecha_nacimiento`
* **Evaluación de Complejidad:** 1 RET y 5 DETs $\implies$ **Complejidad Baja (Low) = 7 PF**.

#### ILF-04: Catálogo de Libros y Control de Stock
* **Tablas físicas agrupadas:** `books`, `book_authors`.
* **Justificación:** Catálogo principal de existencias de libros de la biblioteca y su asociación de autoría múltiple.
* **Tipos de Elementos de Registro (RET): 2 RETs**
  1. `RET 1: Ejemplar Bibliográfico` (tabla `books`).
  2. `RET 2: Co-autoría / Vinculación de Autores` (tabla `book_authors`).
* **Tipos de Elementos de Datos (DET): 10 DETs**
  1. `publicId` (UUID público)
  2. `isbn` (clave normalizada internacional)
  3. `titulo`
  4. `descripcion` (sinopsis)
  5. `editorial`
  6. `url_portada`
  7. `stock_total`
  8. `stock_disponible`
  9. `category_id` (referencia a categoría temática)
  10. `author_id` (referencia a autores asociados)
* **Evaluación de Complejidad:** 2 RETs y 10 DETs $\implies$ **Complejidad Baja (Low) = 7 PF**.

#### ILF-05: Registro Histórico y Activo de Préstamos
* **Tabla física:** `loans`.
* **Justificación:** Registro transaccional que gobierna el ciclo de vida de los préstamos, plazos de devolución y auditoría de morosidad.
* **Tipos de Elementos de Registro (RET): 1 RET**
  1. `RET 1: Préstamo`.
* **Tipos de Elementos de Datos (DET): 7 DETs**
  1. `publicId` (UUID público)
  2. `book_id` (referencia al libro prestado)
  3. `user_id` (referencia al socio prestatario)
  4. `fecha_prestamo`
  5. `fecha_devolucion_esperada`
  6. `fecha_devolucion_efectiva`
  7. `estado` (`PRESTADO`, `DEVUELTO`, `CON_RETRASO`)
* **Evaluación de Complejidad:** 1 RET y 7 DETs $\implies$ **Complejidad Baja (Low) = 7 PF**.

#### EIF-01: Catálogo y Metadatos de Google Books API
* **Entidad / Recurso Externo:** `Google Books VolumeInfo & Item Resource`.
* **Justificación:** Archivo de interfaz externa mantenido por Google fuera de la frontera de la aplicación, referenciado para previsualizar borradores, importar obras y sincronizar metadatos.
* **Tipos de Elementos de Registro (RET): 2 RETs**
  1. `RET 1: VolumeInfo Principal` (título, autores, editorial, sinopsis).
  2. `RET 2: Identificadores y Multimedia` (industryIdentifiers [ISBN-10, ISBN-13], imageLinks [portadas]).
* **Tipos de Elementos de Datos (DET): 12 DETs**
  1. `id` (identificador externo del volumen)
  2. `title`
  3. `subtitle`
  4. `authors`
  5. `publisher`
  6. `publishedDate`
  7. `description`
  8. `isbn_10`
  9. `isbn_13`
  10. `pageCount`
  11. `categories`
  12. `thumbnail_url`
* **Evaluación de Complejidad:** 2 RETs y 12 DETs $\implies$ **Complejidad Baja (Low) = 5 PF**.

---

### 4.2. Resumen Consolidado de Funciones de Datos

| ID | Nombre de la Función de Datos | Tipo | RETs | DETs | Complejidad IFPUG | Puntos de Función (PF) |
| :---: | :--- | :---: | :---: | :---: | :---: | :---: |
| **ILF-01** | Usuarios, Credenciales y Seguridad | ILF | 3 | 16 | Baja | **7** |
| **ILF-02** | Categorías de Libros | ILF | 1 | 3 | Baja | **7** |
| **ILF-03** | Autores de Obras | ILF | 1 | 5 | Baja | **7** |
| **ILF-04** | Catálogo de Libros y Stock | ILF | 2 | 10 | Baja | **7** |
| **ILF-05** | Registro Histórico y Activo de Préstamos | ILF | 1 | 7 | Baja | **7** |
| **EIF-01** | Catálogo y Metadatos de Google Books API | EIF | 2 | 12 | Baja | **5** |
| **TOTAL** | **Funciones de Datos (5 ILF, 1 EIF)** | - | - | - | - | **40 PF** |

---

## 5. Medición de Funciones Transaccionales (Transactional Functions)

A continuación se realiza el análisis sistemático de todos los requisitos funcionales del sistema conforme al `FEATURE-SET.md` y a los endpoints del proyecto, categorizando cada proceso elemental como **Entrada Externa (EI)**, **Salida Externa (EO)** o **Consulta Externa (EQ)**.

> [!IMPORTANT]
> **Desglose de ABM según Regla IFPUG:**
> Siguiendo la premisa metodológica y la instrucción expresa del usuario, las capacidades de ABM (Categorías, Autores, Libros, Usuarios) se descomponen formalmente en **Entradas Externas separadas** para cada acción de cambio de estado (Alta, Modificación, Baja) y en **Consultas Externas** para las recuperaciones de datos.

---

### 5.1. Capacidad 1: Gestión de Autores y Categorías

#### Requisito Funcional RF01: ABM de Categorías de Libros

##### TF-01: Alta de Categoría
* **Endpoint:** `POST /api/categorias`
* **Tipo:** **Entrada Externa (EI)**
* **Propósito Primario:** Registrar una nueva categoría persistiendo sus datos en el sistema. Valida unicidad de nombre.
* **Archivos Tipos Referenciados (FTR): 1 FTR** (`ILF-02 Categorías`).
* **Elementos de Datos (DET): 3 DETs** (`nombre`, `descripcion`, `publicId` en respuesta).
* **Complejidad:** 1 FTR, 3 DET $\implies$ **Baja (Low) = 3 PF**.

##### TF-02: Modificación de Categoría
* **Endpoint:** `PUT /api/categorias/{publicId}`
* **Tipo:** **Entrada Externa (EI)**
* **Propósito Primario:** Modificar el nombre y descripción de una categoría existente identificada por UUID.
* **Archivos Tipos Referenciados (FTR): 1 FTR** (`ILF-02 Categorías`).
* **Elementos de Datos (DET): 4 DETs** (`publicId`, `nombre`, `descripcion`, confirmación de respuesta).
* **Complejidad:** 1 FTR, 4 DET $\implies$ **Baja (Low) = 3 PF**.

##### TF-03: Baja / Eliminación de Categoría
* **Endpoint:** `DELETE /api/categorias/{publicId}`
* **Tipo:** **Entrada Externa (EI)**
* **Propósito Primario:** Eliminar una categoría del sistema previa verificación de la regla de integridad que prohíbe eliminarla si tiene libros asociados (SPI `CategoryDeletionValidator`).
* **Archivos Tipos Referenciados (FTR): 2 FTRs** (`ILF-02 Categorías` [mantenido], `ILF-04 Libros` [leído para validación]).
* **Elementos de Datos (DET): 2 DETs** (`publicId`, código de estado HTTP 204).
* **Complejidad:** 2 FTR, 2 DET $\implies$ **Baja (Low) = 3 PF**.

##### TF-04: Consulta Paginada de Categorías
* **Endpoint:** `GET /api/categorias`
* **Tipo:** **Consulta Externa (EQ)**
* **Propósito Primario:** Recuperar la lista de categorías existentes con ordenamiento y paginación.
* **Archivos Tipos Referenciados (FTR): 1 FTR** (`ILF-02 Categorías`).
* **Elementos de Datos (DET): 6 DETs** (Entrada: `page`, `size`, `sort`; Salida: `publicId`, `nombre`, `descripcion`).
* **Complejidad:** 1 FTR, 6 DET $\implies$ **Baja (Low) = 3 PF**.

##### TF-05: Consulta de Categoría por ID Público
* **Endpoint:** `GET /api/categorias/{publicId}`
* **Tipo:** **Consulta Externa (EQ)**
* **Propósito Primario:** Obtener el detalle de una categoría específica por su UUID.
* **Archivos Tipos Referenciados (FTR): 1 FTR** (`ILF-02 Categorías`).
* **Elementos de Datos (DET): 4 DETs** (Entrada: `publicId`; Salida: `publicId`, `nombre`, `descripcion`).
* **Complejidad:** 1 FTR, 4 DET $\implies$ **Baja (Low) = 3 PF**.

---

#### Requisito Funcional RF02: ABM de Autores

##### TF-06: Alta de Autor
* **Endpoint:** `POST /api/autores`
* **Tipo:** **Entrada Externa (EI)**
* **Propósito Primario:** Incorporar un nuevo autor al catálogo con validación de obligatoriedad de nombre y apellido.
* **Archivos Tipos Referenciados (FTR): 1 FTR** (`ILF-03 Autores`).
* **Elementos de Datos (DET): 5 DETs** (`nombre`, `apellido`, `nacionalidad`, `fechaNacimiento`, `publicId` en respuesta).
* **Complejidad:** 1 FTR, 5 DET $\implies$ **Baja (Low) = 3 PF**.

##### TF-07: Modificación de Autor
* **Endpoint:** `PUT /api/autores/{publicId}`
* **Tipo:** **Entrada Externa (EI)**
* **Propósito Primario:** Modificar los datos biográficos de un autor existente.
* **Archivos Tipos Referenciados (FTR): 1 FTR** (`ILF-03 Autores`).
* **Elementos de Datos (DET): 6 DETs** (`publicId`, `nombre`, `apellido`, `nacionalidad`, `fechaNacimiento`, respuesta).
* **Complejidad:** 1 FTR, 6 DET $\implies$ **Baja (Low) = 3 PF**.

##### TF-08: Baja / Eliminación de Autor
* **Endpoint:** `DELETE /api/autores/{publicId}`
* **Tipo:** **Entrada Externa (EI)**
* **Propósito Primario:** Eliminar un autor del sistema validando mediante SPI (`AuthorDeletionValidator`) que no sea creador de ningún libro registrado.
* **Archivos Tipos Referenciados (FTR): 2 FTRs** (`ILF-03 Autores` [mantenido], `ILF-04 Libros` [leído para validación]).
* **Elementos de Datos (DET): 2 DETs** (`publicId`, código de estado HTTP 204).
* **Complejidad:** 2 FTR, 2 DET $\implies$ **Baja (Low) = 3 PF**.

##### TF-09: Búsqueda y Filtrado de Autores
* **Endpoint:** `GET /api/autores`
* **Tipo:** **Consulta Externa (EQ)**
* **Propósito Primario:** Recuperar la lista de autores aplicando filtros opcionales de búsqueda parcial (`q` en nombre/apellido) y nacionalidad.
* **Archivos Tipos Referenciados (FTR): 1 FTR** (`ILF-03 Autores`).
* **Elementos de Datos (DET): 6 DETs** (Entrada: `q`, `nacionalidad`; Salida: `publicId`, `nombre`, `apellido`, `nacionalidad`, `fechaNacimiento`).
* **Complejidad:** 1 FTR, 6 DET $\implies$ **Baja (Low) = 3 PF**.

##### TF-10: Consulta de Autor por ID Público
* **Endpoint:** `GET /api/autores/{publicId}`
* **Tipo:** **Consulta Externa (EQ)**
* **Propósito Primario:** Consultar la información detallada de un autor por su UUID.
* **Archivos Tipos Referenciados (FTR): 1 FTR** (`ILF-03 Autores`).
* **Elementos de Datos (DET): 5 DETs** (Entrada: `publicId`; Salida: `publicId`, `nombre`, `apellido`, `nacionalidad`, `fechaNacimiento`).
* **Complejidad:** 1 FTR, 5 DET $\implies$ **Baja (Low) = 3 PF**.

---

### 5.2. Capacidad 2: Gestión de Catálogo de Libros y Stock

#### Requisito Funcional RF03: Alta y Edición de Libros

##### TF-11: Alta Manual de Libro
* **Endpoint:** `POST /api/v1/books`
* **Tipo:** **Entrada Externa (EI)**
* **Propósito Primario:** Registrar un nuevo libro con stock total inicial. Valida unicidad global de ISBN, existencia previa de categoría y autores, e inicializa atómicamente `stockDisponible = stockTotal`.
* **Archivos Tipos Referenciados (FTR): 3 FTRs** (`ILF-04 Libros` [mantenido], `ILF-02 Categorías` [leído], `ILF-03 Autores` [leído]).
* **Elementos de Datos (DET): 9 DETs** (`isbn`, `titulo`, `descripcion`, `editorial`, `urlPortada`, `stockTotal`, `categoriaPublicId`, `autoresIds`, `publicId` en respuesta).
* **Complejidad:** 3 FTRs, 9 DETs $\implies$ **Media (Average) = 4 PF**.

##### TF-12: Actualización Completa de Libro
* **Endpoint:** `PUT /api/v1/books/{isbn}`
* **Tipo:** **Entrada Externa (EI)**
* **Propósito Primario:** Modificar íntegramente los datos y el stock total de un libro. Aplica la regla `BR-010`: el nuevo stock no puede ser inferior a las copias prestadas actualmente.
* **Archivos Tipos Referenciados (FTR): 4 FTRs** (`ILF-04 Libros` [mantenido], `ILF-02 Categorías`, `ILF-03 Autores`, `ILF-05 Préstamos` [leído para verificar préstamos vigentes]).
* **Elementos de Datos (DET): 9 DETs** (`isbn`, `titulo`, `descripcion`, `editorial`, `urlPortada`, `stockTotal`, `categoriaPublicId`, `autoresIds`, respuesta).
* **Complejidad:** 4 FTRs, 9 DETs $\implies$ **Media (Average) = 4 PF**.

##### TF-13: Modificación Parcial de Metadatos de Libro (Patch)
* **Endpoint:** `PATCH /api/v1/books/{isbn}`
* **Tipo:** **Entrada Externa (EI)**
* **Propósito Primario:** Actualizar selectivamente metadatos descriptivos (título, descripción, editorial, categoría, autores) sin alterar las existencias físicas.
* **Archivos Tipos Referenciados (FTR): 3 FTRs** (`ILF-04 Libros` [mantenido], `ILF-02 Categorías`, `ILF-03 Autores`).
* **Elementos de Datos (DET): 7 DETs** (`isbn`, `titulo`, `descripcion`, `editorial`, `categoriaPublicId`, `autoresPublicIds`, respuesta).
* **Complejidad:** 3 FTRs, 7 DETs $\implies$ **Media (Average) = 4 PF**.

##### TF-14: Actualización de Portada del Libro
* **Endpoint:** `PATCH /api/v1/books/{isbn}/portada`
* **Tipo:** **Entrada Externa (EI)**
* **Propósito Primario:** Actualizar de forma atómica la URL de la imagen de portada de la obra.
* **Archivos Tipos Referenciados (FTR): 1 FTR** (`ILF-04 Libros`).
* **Elementos de Datos (DET): 3 DETs** (`isbn`, `urlPortada`, respuesta).
* **Complejidad:** 1 FTR, 3 DETs $\implies$ **Baja (Low) = 3 PF**.

---

#### Requisito Funcional RF04: Búsqueda y Filtrado del Catálogo

##### TF-15: Búsqueda Multicriterio de Libros en Catálogo
* **Endpoint:** `GET /api/v1/books`
* **Tipo:** **Consulta Externa (EQ)**
* **Propósito Primario:** Búsqueda dinámica con combinación de filtros (título parcial, ISBN, categoría, autor, y bandera `soloDisponibles`) con paginación y ordenamiento.
* **Archivos Tipos Referenciados (FTR): 3 FTRs** (`ILF-04 Libros`, `ILF-02 Categorías`, `ILF-03 Autores`).
* **Elementos de Datos (DET): 14 DETs** (Entrada: `titulo`, `isbn`, `categoriaPublicId`, `autorPublicId`, `soloDisponibles`, `page`, `size`, `sort`; Salida: `publicId`, `isbn`, `titulo`, `stockTotal`, `stockDisponible`, `categoria`, `autores`).
* **Complejidad:** 3 FTRs, 14 DETs $\implies$ **Media (Average) = 4 PF**.

##### TF-16: Consulta de Libro por ISBN
* **Endpoint:** `GET /api/v1/books/{isbn}`
* **Tipo:** **Consulta Externa (EQ)**
* **Propósito Primario:** Recuperar la ficha bibliográfica exhaustiva de una obra por su código ISBN.
* **Archivos Tipos Referenciados (FTR): 3 FTRs** (`ILF-04 Libros`, `ILF-02 Categorías`, `ILF-03 Autores`).
* **Elementos de Datos (DET): 10 DETs** (Entrada: `isbn`; Salida: `publicId`, `isbn`, `titulo`, `descripcion`, `editorial`, `urlPortada`, `stockTotal`, `stockDisponible`, `categoria`, `autores`).
* **Complejidad:** 3 FTRs, 10 DETs $\implies$ **Media (Average) = 4 PF**.

---

#### Requisito Funcional RF05: Ajuste Manual de Stock

##### TF-17: Ajuste Físico de Inventario de Libro
* **Endpoint:** `PATCH /api/v1/books/{isbn}/stock`
* **Tipo:** **Entrada Externa (EI)**
* **Propósito Primario:** Modificar la cantidad física total de ejemplares asentando compras, roturas o donaciones. Computa las copias en préstamo ($\text{stockTotal} - \text{stockDisponible}$), verifica que el nuevo stock cubra los préstamos vigentes y recalcula atómicamente el stock disponible.
* **Archivos Tipos Referenciados (FTR): 2 FTRs** (`ILF-04 Libros` [mantenido], `ILF-05 Préstamos` [leído para validar copias activas]).
* **Elementos de Datos (DET): 4 DETs** (`isbn`, `nuevoStockTotal`, `stockTotal` recalculado, `stockDisponible` recalculado).
* **Complejidad:** 2 FTRs, 4 DETs $\implies$ **Baja (Low) = 3 PF**.

---

### 5.3. Capacidad 3: Integración Externa y Curaduría (Google Books)

#### Requisito Funcional RF16 (Google Books): Importación Directa

##### TF-18: Importación Directa desde Google Books API
* **Endpoint:** `POST /api/v1/books/import`
* **Tipo:** **Entrada Externa (EI)**
* **Propósito Primario:** Ingesta automática de un libro enviando únicamente ISBN y copias deseadas. Consulta la API externa de Google Books, mapea metadatos, da de alta o vincula dinámicamente autores y categorías inexistentes y persiste el libro con stock inicial.
* **Archivos Tipos Referenciados (FTR): 4 FTRs** (`ILF-04 Libros` [mantenido], `ILF-02 Categorías` [mantenido/leído], `ILF-03 Autores` [mantenido/leído], `EIF-01 Google Books API` [leído externamente]).
* **Elementos de Datos (DET): 11 DETs** (Entrada: `isbn`, `totalCopies`; Datos mapeados: `titulo`, `descripcion`, `editorial`, `urlPortada`, `categorias`, `autores`; Respuesta: `publicId`, `stockTotal`, `stockDisponible`).
* **Complejidad:** 4 FTRs, 11 DETs $\implies$ **Alta (High) = 6 PF**.

---

#### Requisito Funcional RF19: Previsualización y Curaduría (Pre-fill)

##### TF-19: Previsualización de Borrador de Libro Externo (Pre-fill)
* **Endpoint:** `GET /api/v1/books/external/isbn/{isbn}`
* **Tipo:** **Consulta Externa (EQ)**
* **Propósito Primario:** Consultar Google Books para previsualizar los metadatos de un ISBN sin persistir, verificando si ya existe en el catálogo local (`alreadyExistsInLocalCatalog`) para permitir la edición humana antes del alta definitiva.
* **Archivos Tipos Referenciados (FTR): 2 FTRs** (`EIF-01 Google Books API` [leído], `ILF-04 Libros` [leído para verificar duplicidad local]).
* **Elementos de Datos (DET): 9 DETs** (Entrada: `isbn`; Salida: `isbn`, `titulo`, `autores`, `editorial`, `descripcion`, `urlPortada`, `alreadyExistsInLocalCatalog`, `sugerenciaCategoria`).
* **Complejidad:** 2 FTRs, 9 DETs $\implies$ **Media (Average) = 4 PF**.

---

#### Requisito Funcional RF17: Sincronización y Enriquecimiento de Metadatos

##### TF-20: Sincronización de Metadatos con Google Books API
* **Endpoint:** `POST /api/v1/books/{identifier}/sync-google-books`
* **Tipo:** **Entrada Externa (EI)**
* **Propósito Primario:** Actualizar metadatos faltantes en libros locales desde Google Books de forma no destructiva. Si `force=false` solo completa nulos; si `force=true` sobreescribe descripciones o portadas sin tocar nunca el stock ni préstamos.
* **Archivos Tipos Referenciados (FTR): 2 FTRs** (`ILF-04 Libros` [mantenido], `EIF-01 Google Books API` [leído]).
* **Elementos de Datos (DET): 7 DETs** (`identifier`, `force`, `titulo`, `descripcion`, `editorial`, `urlPortada`, respuesta).
* **Complejidad:** 2 FTRs, 7 DETs $\implies$ **Media (Average) = 4 PF**.

---

### 5.4. Capacidad 4: Gestión de Usuarios, Autenticación y Cuentas

#### Requisito Funcional RF06 / RF16: Registro y Confirmación de Cuenta

##### TF-21: Registro de Nuevo Socio
* **Endpoint:** `POST /api/v1/auth/register`
* **Tipo:** **Entrada Externa (EI)**
* **Propósito Primario:** Dar de alta a un usuario visitante con rol `ROLE_SOCIO` en estado inactivo (`enabled=false`), encriptar su clave con BCrypt, generar un token de verificación temporal en base de datos y disparar el evento de envío de correo.
* **Archivos Tipos Referenciados (FTR): 3 FTRs** (`ILF-01 Usuarios y Seguridad` [mantenido en users, credentials y tokens]).
* **Elementos de Datos (DET): 8 DETs** (`username`, `password`, `nombre`, `apellido`, `email`, `dni`, `telefono`, respuesta DTO).
* **Complejidad:** 3 FTRs, 8 DETs $\implies$ **Media (Average) = 4 PF**.

##### TF-22: Confirmación y Activación de Cuenta
* **Endpoint:** `POST /api/v1/auth/confirm-account`
* **Tipo:** **Entrada Externa (EI)**
* **Propósito Primario:** Validar el token de confirmación recibido por email (verificando vigencia < 15 min y que no haya sido consumido), activar la cuenta del usuario (`enabled=true`) y marcar el token como utilizado.
* **Archivos Tipos Referenciados (FTR): 3 FTRs** (`ILF-01 Usuarios y Seguridad` [actualiza tokens, users y credentials]).
* **Elementos de Datos (DET): 3 DETs** (`token`, estado del usuario, mensaje de confirmación).
* **Complejidad:** 3 FTRs, 3 DETs $\implies$ **Baja (Low) = 3 PF**.

##### TF-23: Reenvío de Token de Activación
* **Endpoint:** `POST /api/v1/auth/resend-verification`
* **Tipo:** **Entrada Externa (EI)**
* **Propósito Primario:** Generar y almacenar un nuevo token de activación invalidando los anteriores si la cuenta está pendiente de confirmación, respondiendo con mensaje genérico anti-enumeración.
* **Archivos Tipos Referenciados (FTR): 2 FTRs** (`ILF-01 Usuarios y Seguridad` [leído en users, actualizado en tokens]).
* **Elementos de Datos (DET): 3 DETs** (`email`, estado, mensaje de respuesta).
* **Complejidad:** 2 FTRs, 3 DETs $\implies$ **Baja (Low) = 3 PF**.

---

#### Requisito Funcional RF15: Restablecimiento de Contraseña

##### TF-24: Solicitud de Recuperación de Contraseña (Forgot Password)
* **Endpoint:** `POST /api/v1/auth/forgot-password`
* **Tipo:** **Entrada Externa (EI)**
* **Propósito Primario:** Procesar la solicitud de olvido de clave. Si el email existe, genera un token con TTL de 15 minutos en `password_reset_tokens` y dispara el correo. Aplica respuesta genérica anti-enumeración (`200 OK`).
* **Archivos Tipos Referenciados (FTR): 2 FTRs** (`ILF-01 Usuarios y Seguridad` [leído en users, mantenido en tokens]).
* **Elementos de Datos (DET): 3 DETs** (`email`, estado, mensaje genérico anti-enumeración).
* **Complejidad:** 2 FTRs, 3 DETs $\implies$ **Baja (Low) = 3 PF**.

##### TF-25: Reestablecimiento de Contraseña con Token (Reset Password)
* **Endpoint:** `POST /api/v1/auth/reset-password`
* **Tipo:** **Entrada Externa (EI)**
* **Propósito Primario:** Validar el token de reseteo, codificar la nueva contraseña con BCrypt, actualizar la entidad de credenciales, marcar el token consumido e invalidar otros tokens activos.
* **Archivos Tipos Referenciados (FTR): 3 FTRs** (`ILF-01 Usuarios y Seguridad` [mantenido en credentials, tokens y users]).
* **Elementos de Datos (DET): 4 DETs** (`token`, `newPassword`, confirmación, mensaje de éxito).
* **Complejidad:** 3 FTRs, 4 DETs $\implies$ **Baja (Low) = 3 PF**.

---

#### Gestión de Sesión y Autenticación (F-01 en FEATURE-SET)

##### TF-26: Inicio de Sesión / Autenticación (Login)
* **Endpoint:** `POST /api/v1/auth/login`
* **Tipo:** **Entrada Externa (EI)**
* **Propósito Primario:** Autenticar credenciales de usuario (username/password), verificar que la cuenta esté habilitada (`enabled=true`), generar par de tokens JWT y **persistir el nuevo refresh_token en la base de datos** (`credentials.refresh_token`) para control de sesiones activas. Al modificar un ILF, clasifica como EI.
* **Archivos Tipos Referenciados (FTR): 2 FTRs** (`ILF-01 Usuarios y Seguridad` [actualiza credentials, lee users y roles]).
* **Elementos de Datos (DET): 4 DETs** (`username`, `password`, `accessToken`, `refreshToken`).
* **Complejidad:** 2 FTRs, 4 DETs $\implies$ **Baja (Low) = 3 PF**.

##### TF-27: Renovación y Rotación de Sesión (Refresh Token)
* **Endpoint:** `POST /api/v1/auth/refresh`
* **Tipo:** **Entrada Externa (EI)**
* **Propósito Primario:** Validar vigencia del Refresh Token contra la base de datos, implementar rotación generando un nuevo par de tokens y persistiendo el nuevo Refresh Token en `credentials`.
* **Archivos Tipos Referenciados (FTR): 1 FTR** (`ILF-01 Usuarios y Seguridad`).
* **Elementos de Datos (DET): 3 DETs** (`refreshToken` recibido, nuevo `accessToken`, nuevo `refreshToken`).
* **Complejidad:** 1 FTR, 3 DETs $\implies$ **Baja (Low) = 3 PF**.

---

#### Requisito Funcional RF07: Consulta de Perfil e Historial Propio

##### TF-28: Consulta de Perfil Personal (Self-Only)
* **Endpoint:** `GET /api/usuarios/me`
* **Tipo:** **Consulta Externa (EQ)**
* **Propósito Primario:** Recuperar los datos personales del usuario autenticado en la sesión actual y un resumen de sus préstamos recientes (principio estricto de auto-consulta).
* **Archivos Tipos Referenciados (FTR): 2 FTRs** (`ILF-01 Usuarios`, `ILF-05 Préstamos`).
* **Elementos de Datos (DET): 8 DETs** (Salida: `publicId`, `nombre`, `apellido`, `email`, `dni`, `telefono`, `rol`, lista de préstamos recientes).
* **Complejidad:** 2 FTRs, 8 DETs $\implies$ **Media (Average) = 4 PF**.

##### TF-29: Consulta de Historial Personal de Préstamos
* **Endpoint:** `GET /api/usuarios/me/prestamos`
* **Tipo:** **Consulta Externa (EQ)**
* **Propósito Primario:** Recuperar el listado paginado y cronológico de todos los préstamos solicitados históricamente por el socio autenticado.
* **Archivos Tipos Referenciados (FTR): 2 FTRs** (`ILF-05 Préstamos`, `ILF-04 Libros`).
* **Elementos de Datos (DET): 10 DETs** (Entrada: `page`, `size`, `sort`; Salida: `publicId`, `fechaPrestamo`, `fechaDevolucionEsperada`, `fechaDevolucionEfectiva`, `estado`, `isbn`, `tituloLibro`).
* **Complejidad:** 2 FTRs, 10 DETs $\implies$ **Media (Average) = 4 PF**.

---

#### Requisito Funcional RF08: Administración de Usuarios y Roles

##### TF-30: Modificación del Estado de Cuenta de Usuario
* **Endpoint:** `PATCH /api/usuarios/{publicId}/estado`
* **Tipo:** **Entrada Externa (EI)**
* **Propósito Primario:** Activar o inhabilitar una cuenta de socio. Aplica la regla `BR-022` (autoprotección del bibliotecario contra autobloqueo) y la regla `BR-023` (prohibido desactivar usuarios con préstamos activos o en mora). Sincroniza `users.activo` y `credentials.enabled`.
* **Archivos Tipos Referenciados (FTR): 3 FTRs** (`ILF-01 Usuarios y Seguridad` [mantenido], `ILF-05 Préstamos` [leído para validar ausencia de préstamos activos]).
* **Elementos de Datos (DET): 4 DETs** (`publicId`, `activo`, estado resultante, respuesta DTO).
* **Complejidad:** 3 FTRs, 4 DETs $\implies$ **Baja (Low) = 3 PF**.

##### TF-31: Modificación de Rol Institucional de Usuario
* **Endpoint:** `PATCH /api/usuarios/{publicId}/rol`
* **Tipo:** **Entrada Externa (EI)**
* **Propósito Primario:** Asignar nuevo rol (`SOCIO` o `BIBLIOTECARIO`). Aplica regla de autoprotección (no rebajar propio rol) y actualiza tablas de usuario y roles.
* **Archivos Tipos Referenciados (FTR): 2 FTRs** (`ILF-01 Usuarios y Seguridad` [actualiza `users` y `credentials_roles`]).
* **Elementos de Datos (DET): 4 DETs** (`publicId`, `nuevoRol`, confirmación, respuesta).
* **Complejidad:** 2 FTRs, 4 DETs $\implies$ **Baja (Low) = 3 PF**.

---

### 5.5. Capacidad 5: Gestión de Préstamos y Devoluciones (Lógica Core)

#### Requisito Funcional RF09: Solicitud y Registro de Préstamos

##### TF-32: Solicitud y Concesión de Préstamo
* **Endpoint:** `POST /api/prestamos`
* **Tipo:** **Entrada Externa (EI)**
* **Propósito Primario:** Crear un préstamo físico. Valida 4 invariantes de negocio: socio activo (`activo=true`), stock disponible $> 0$, tope de 3 préstamos activos simultáneos (`BR-027`), y cero tolerancia a préstamos vencidos en mora (`BR-028`). Fija fecha límite a +14 días, decrementa atómicamente el `stockDisponible` en 1 y emite evento para comprobante.
* **Archivos Tipos Referenciados (FTR): 3 FTRs** (`ILF-05 Préstamos` [mantenido], `ILF-04 Libros` [mantenido en stock], `ILF-01 Usuarios` [leído]).
* **Elementos de Datos (DET): 8 DETs** (Entrada: `bookPublicId`, `usuarioPublicId` opcional; Salida: `publicId`, `fechaPrestamo`, `fechaDevolucionEsperada`, `estado`, libro, socio).
* **Complejidad:** 3 FTRs, 8 DETs $\implies$ **Media (Average) = 4 PF**.

---

#### Requisito Funcional RF10: Registro de Devolución e Inspección de Plazos

##### TF-33: Registro de Devolución Física del Ejemplar
* **Endpoint:** `PUT /api/prestamos/{publicId}/devolucion`
* **Tipo:** **Entrada Externa (EI)**
* **Propósito Primario:** Asentar el retorno material del libro. Valida que el préstamo esté en estado `PRESTADO`, registra la fecha efectiva actual, compara fechas para transicionar a `DEVUELTO` (en término) o `CON_RETRASO` (tardío), e incrementa atómicamente en 1 el `stockDisponible` restituyendo el inventario.
* **Archivos Tipos Referenciados (FTR): 2 FTRs** (`ILF-05 Préstamos` [mantenido], `ILF-04 Libros` [mantenido en stock]).
* **Elementos de Datos (DET): 7 DETs** (`publicId`, `fechaDevolucionEfectiva`, `estado`, `fechaDevolucionEsperada`, `stockActualizado`, libro, usuario).
* **Complejidad:** 2 FTRs, 7 DETs $\implies$ **Media (Average) = 4 PF**.

---

#### Requisito Funcional RF11 (Supervisión): Supervisión y Monitoreo de Préstamos

##### TF-34: Panel de Supervisión y Auditoría de Préstamos
* **Endpoint:** `GET /api/prestamos/supervision`
* **Tipo:** **Salida Externa (EO)**
* **Propósito Primario:** Proveer al personal bibliotecario un panel de control con filtros por estado, solo atrasados y socio. **Cálculo de datos derivados:** Ejecuta la fórmula matemática `calculateDiasAtraso` en tiempo real ($\text{diasAtraso} = \text{fechaActual} - \text{fechaDevolucionEsperada}$) exponiendo el atraso numérico. Según el estándar IFPUG CPM 4.2.1, toda consulta que calcule o derive datos matemáticamente clasifica estrictamente como **Salida Externa (EO)**.
* **Archivos Tipos Referenciados (FTR): 3 FTRs** (`ILF-05 Préstamos`, `ILF-04 Libros`, `ILF-01 Usuarios`).
* **Elementos de Datos (DET): 13 DETs** (Filtros: `estado`, `soloAtrasados`, `usuarioPublicId`, `pageable`; Salida: `publicId`, `usuario`, `libro`, `fechaPrestamo`, `fechaDevolucionEsperada`, `fechaDevolucionEfectiva`, `estado`, `diasAtraso` [derivado]).
* **Complejidad:** 3 FTRs, 13 DETs $\implies$ **Media (Average) = 5 PF**.

---

### 5.6. Capacidad 6: Comunicaciones y Notificaciones Automatizadas

#### Requisito Funcional RF13: Notificación Instantánea de Alta de Préstamo

##### TF-35: Emisión de Comprobante Digital de Préstamo
* **Disparador:** Evento de dominio `LoanCreatedEvent` tras confirmación en BD (`AFTER_COMMIT`).
* **Tipo:** **Salida Externa (EO)**
* **Propósito Primario:** Componer y despachar un comprobante formal en formato HTML al correo del socio. Deriva y calcula el plazo exacto de vencimiento (+14 días corridos) y envía los datos fuera de la frontera hacia el servidor SMTP.
* **Archivos Tipos Referenciados (FTR): 3 FTRs** (`ILF-05 Préstamos`, `ILF-04 Libros`, `ILF-01 Usuarios`).
* **Elementos de Datos (DET): 6 DETs** (`emailSocio`, `nombreSocio`, `tituloLibro`, `fechaEmision`, `fechaLimiteDevolucion`, comprobante digital HTML).
* **Complejidad:** 3 FTRs, 6 DETs $\implies$ **Media (Average) = 5 PF**.

---

#### Requisito Funcional RF14: Tarea Programada de Recordatorio de Devolución (48hs)

##### TF-36: Recordatorio Preventivo Diario de Vencimiento de Préstamos
* **Disparador:** Tarea programada (Scheduler con expresión cron `0 0 8 * * *`).
* **Tipo:** **Salida Externa (EO)**
* **Propósito Primario:** Proceso por lotes que barre diariamente la base de datos identificando préstamos activos cuya fecha esperada venza a las 48 horas exactas ($\text{targetDate} = \text{hoy} + 2 \text{ días}$), calcula los días restantes y despacha notificaciones personalizadas vía SMTP a cada socio.
* **Archivos Tipos Referenciados (FTR): 3 FTRs** (`ILF-05 Préstamos`, `ILF-04 Libros`, `ILF-01 Usuarios`).
* **Elementos de Datos (DET): 6 DETs** (`targetDate`, `emailSocio`, `nombreSocio`, `tituloLibro`, `fechaDevolucionEsperada`, mensaje HTML preventivo).
* **Complejidad:** 3 FTRs, 6 DETs $\implies$ **Media (Average) = 5 PF**.

---

### 5.7. Resumen Consolidado de Funciones Transaccionales

| ID | Nombre de la Transacción | Tipo | FTR | DET | Complejidad IFPUG | Puntos de Función (PF) |
| :---: | :--- | :---: | :---: | :---: | :---: | :---: |
| **TF-01** | Alta de Categoría | EI | 1 | 3 | Baja | **3** |
| **TF-02** | Modificación de Categoría | EI | 1 | 4 | Baja | **3** |
| **TF-03** | Baja / Eliminación de Categoría | EI | 2 | 2 | Baja | **3** |
| **TF-04** | Consulta Paginada de Categorías | EQ | 1 | 6 | Baja | **3** |
| **TF-05** | Consulta de Categoría por ID Público | EQ | 1 | 4 | Baja | **3** |
| **TF-06** | Alta de Autor | EI | 1 | 5 | Baja | **3** |
| **TF-07** | Modificación de Autor | EI | 1 | 6 | Baja | **3** |
| **TF-08** | Baja / Eliminación de Autor | EI | 2 | 2 | Baja | **3** |
| **TF-09** | Búsqueda y Filtrado de Autores | EQ | 1 | 6 | Baja | **3** |
| **TF-10** | Consulta de Autor por ID Público | EQ | 1 | 5 | Baja | **3** |
| **TF-11** | Alta Manual de Libro | EI | 3 | 9 | Media | **4** |
| **TF-12** | Actualización Completa de Libro | EI | 4 | 9 | Media | **4** |
| **TF-13** | Modificación Parcial de Libro (Patch) | EI | 3 | 7 | Media | **4** |
| **TF-14** | Actualización de Portada de Libro | EI | 1 | 3 | Baja | **3** |
| **TF-15** | Búsqueda Multicriterio de Libros | EQ | 3 | 14 | Media | **4** |
| **TF-16** | Consulta de Libro por ISBN | EQ | 3 | 10 | Media | **4** |
| **TF-17** | Ajuste Manual de Stock de Libro | EI | 2 | 4 | Baja | **3** |
| **TF-18** | Importación Directa desde Google Books | EI | 4 | 11 | Alta | **6** |
| **TF-19** | Previsualización de Borrador (Pre-fill) | EQ | 2 | 9 | Media | **4** |
| **TF-20** | Sincronización con Google Books | EI | 2 | 7 | Media | **4** |
| **TF-21** | Registro de Nuevo Socio | EI | 3 | 8 | Media | **4** |
| **TF-22** | Confirmación y Activación de Cuenta | EI | 3 | 3 | Baja | **3** |
| **TF-23** | Reenvío de Token de Activación | EI | 2 | 3 | Baja | **3** |
| **TF-24** | Solicitud de Reseteo (Forgot Password) | EI | 2 | 3 | Baja | **3** |
| **TF-25** | Reestablecimiento de Contraseña (Reset) | EI | 3 | 4 | Baja | **3** |
| **TF-26** | Inicio de Sesión (Login JWT) | EI | 2 | 4 | Baja | **3** |
| **TF-27** | Renovación y Rotación de Sesión (Refresh)| EI | 1 | 3 | Baja | **3** |
| **TF-28** | Consulta de Perfil Personal (Me) | EQ | 2 | 8 | Media | **4** |
| **TF-29** | Consulta de Historial Propio de Préstamos | EQ | 2 | 10 | Media | **4** |
| **TF-30** | Modificación de Estado de Cuenta | EI | 3 | 4 | Baja | **3** |
| **TF-31** | Modificación de Rol de Usuario | EI | 2 | 4 | Baja | **3** |
| **TF-32** | Solicitud y Concesión de Préstamo | EI | 3 | 8 | Media | **4** |
| **TF-33** | Registro de Devolución Física de Libro | EI | 2 | 7 | Media | **4** |
| **TF-34** | Supervisión y Auditoría de Préstamos | EO | 3 | 13 | Media | **5** |
| **TF-35** | Notificación Comprobante de Préstamo | EO | 3 | 6 | Media | **5** |
| **TF-36** | Scheduler Recordatorio 48hs Vencimiento | EO | 3 | 6 | Media | **5** |
| **TOTAL** | **Funciones Transaccionales (24 EI, 3 EO, 9 EQ)** | - | - | - | - | **129 PF** |

---

## 6. Análisis y Justificación del Factor de Ajuste (VAF / GSC)

El Factor de Ajuste del Valor (VAF) evalúa el impacto de **14 Características Generales del Sistema (GSC)**, cada una puntuada de 0 a 5 según el manual IFPUG CPM 4.2.1. A continuación se analiza cada característica fundamentada en la arquitectura técnica y funcional del proyecto:

### 6.1. Evaluación Individual de las 14 Características

#### 1. Comunicación de Datos (Data Communications)
* **Puntaje Asignado:** **3**
* **Justificación:** La aplicación es un servicio backend puramente online que se comunica exclusivamente mediante el protocolo HTTP/HTTPS utilizando formatos JSON estándar para entrada y salida, interactúa mediante WebClient reactivo con la API externa de Google Books y envía mensajes SMTP. La comunicación de datos en línea es fundamental para el funcionamiento interactivo del sistema.

#### 2. Procesamiento Distribuido de Datos (Distributed Data Processing)
* **Puntaje Asignado:** **2**
* **Justificación:** La arquitectura sigue un modelo cliente-servidor distribuido. El backend interactúa de manera desacoplada con un motor de base de datos relacional independiente (PostgreSQL/H2), procesa y transfiere datos bidireccionalmente hacia/desde la API externa de Google Books y despacha tareas asíncronas hacia el servidor de correo electrónico SMTP.

#### 3. Rendimiento (Performance)
* **Puntaje Asignado:** **2**
* **Justificación:** Se establecen requisitos de respuesta ágil en la consulta de catálogos y préstamos. El sistema implementa estrategias deliberadas de diseño para salvaguardar el rendimiento: índices B-Tree en ISBN, emails y UUIDs, despacho asíncrono no bloqueante de correos electrónicos (`@Async` en segundo plano para no demorar la respuesta HTTP), y consultas diferidas (`FetchType.LAZY`) para mitigar sobrecargas $N+1$.

#### 4. Configuración Fuertemente Utilizada (Heavily Used Configuration)
* **Puntaje Asignado:** **1**
* **Justificación:** El sistema opera sobre infraestructura estándar de contenedores y servidores Java/Spring Boot. No existen restricciones extraordinarias de hardware o memoria que fuercen particionamientos extremos ni requerimientos de hardware especializado.

#### 5. Tasa de Transacciones (Transaction Rate)
* **Puntaje Asignado:** **2**
* **Justificación:** La aplicación experimenta picos de concurrencia diaria coincidentes con los horarios de atención y retiro/devolución física de la biblioteca institucional, además de la ejecución matutina concentrada de tareas programadas por lotes (scheduler diario a las 08:00 AM).

#### 6. Entrada de Datos en Línea (On-Line Data Entry)
* **Puntaje Asignado:** **5**
* **Justificación:** Más del 95% de las transacciones del sistema (altas, modificaciones, ajustes de inventario, solicitudes de préstamo, devoluciones, registros de usuario, confirmación de cuentas y búsquedas) se procesan de forma interactiva y en tiempo real a través de los endpoints de la API REST.

#### 7. Eficiencia del Usuario Final (End-User Efficiency)
* **Puntaje Asignado:** **3**
* **Justificación:** La solución incluye múltiples mecanismos diseñados para maximizar la productividad y minimizar errores operativos: previsualización de borradores (Pre-fill) para evitar carga manual reiterada, importación directa con auto-asociación de autores/categorías, ordenamiento y paginación predeterminados, cálculo automático de fechas límites de devolución (+14 días) y respuestas de error estandarizadas RFC 7807 (`ProblemDetail`).

#### 8. Actualización en Línea (On-Line Update)
* **Puntaje Asignado:** **4**
* **Justificación:** Todos los archivos lógicos internos se actualizan de forma interactiva e inmediata en línea. Además, la protección contra la pérdida o inconsistencia de datos es crítica: el sistema implementa demarcaciones transaccionales estrictas (`@Transactional`), rollbacks automáticos ante fallas y control atómico de decremento e incremento de existencias en préstamos y devoluciones.

#### 9. Procesamiento Complejo (Complex Processing)
* **Puntaje Asignado:** **3**
* **Justificación:** La aplicación contiene:
  1. Seguridad y control de acceso riguroso (Spring Security, roles RBAC `SOCIO`/`BIBLIOTECARIO`, hashes BCrypt, rotación de refresh tokens, protección anti-enumeración y reglas de autoprotección contra auto-desactivación de bibliotecarios).
  2. Lógica de negocio multicondicional extensa (evaluación de stock disponible, mora activa, cupo de 3 préstamos máximos, transiciones automáticas de puntualidad `DEVUELTO` vs `CON_RETRASO`).
  3. Procesamiento de excepciones y tolerancia a fallos desacoplada (política failsafe en envío de emails para no revertir transacciones confirmadas de negocio).

#### 10. Reusabilidad (Reusability)
* **Puntaje Asignado:** **2**
* **Justificación:** La aplicación está diseñada bajo principios de arquitectura modular y hexagonal (puertos y adaptadores para el subsistema de correo y cliente Google Books), interfaces desacopladas SPI (`AuthorDeletionValidator`, `CategoryDeletionValidator`) y componentes transversales compartidos (DTOs reutilizables, manejador centralizado de errores `@RestControllerAdvice`).

#### 11. Facilidad de Instalación (Installation Ease)
* **Puntaje Asignado:** **2**
* **Justificación:** Cuenta con empaquetado autocontenido (Spring Boot JAR ejecutable con wrapper Maven `mvnw`), aprovisionamiento automatizado del esquema relacional mediante Hibernate DDL, y perfiles de configuración desacoplados mediante variables de entorno (`.env` y `application.yml`).

#### 12. Facilidad Operativa (Operational Ease)
* **Puntaje Asignado:** **2**
* **Justificación:** El sistema automatiza tareas que típicamente requerirían intervención humana manual, tales como el barrido y despacho diario de recordatorios de vencimiento (Scheduler) y el despacho reactivo de comprobantes digitales tras cada confirmación transaccional (`@TransactionalEventListener(phase = AFTER_COMMIT)`).

#### 13. Múltiples Sitios (Multiple Sites)
* **Puntaje Asignado:** **0**
* **Justificación:** Conforme se explicita en la sección 3.2 de `FEATURE-SET.md`, el sistema está concebido para un inventario único y una única sede institucional centralizada. No administra múltiples sucursales ni sedes físicas heterogéneas.

#### 14. Facilidad de Cambios (Facilitate Change)
* **Puntaje Asignado:** **3**
* **Justificación:** El diseño desacoplado en capas, el uso de contratos basados en interfaces y adaptadores para proveedores externos, y el mapeo dual de rutas (`/api/v1` y `/api`) facilitan modificaciones evolutivas sin quebrar clientes legados ni alterar el núcleo de dominio.

---

### 6.2. Cálculo del Factor de Ajuste del Valor (VAF)

Sumatoria de las 14 Características Generales del Sistema:

$$TDI = 3 + 2 + 2 + 1 + 2 + 5 + 3 + 4 + 3 + 2 + 2 + 2 + 0 + 3 = 33$$

Aplicando la fórmula matemática del estándar IFPUG:

$$VAF = (TDI \times 0.01) + 0.65$$
$$VAF = (33 \times 0.01) + 0.65 = 0.33 + 0.65 = \mathbf{0.98}$$

> [!NOTE]
> Un factor de ajuste $VAF = 0.98$ refleja de manera fiel y equilibrada un sistema backend empresarial con fuerte componente transaccional online, alta rigurosidad en seguridad y reglas de negocio, pero sin complejidades extremas de distribución multisede o computación científica en tiempo real dura.

---

## 7. Cálculo Final y Consolidación de Resultados

### 7.1. Matriz Resumen de Puntos de Función No Ajustados (UFP)

| Componente Funcional | Baja (Low) | Media (Average) | Alta (High) | Total de Funciones | Subtotal Puntos de Función (PF) |
| :--- | :---: | :---: | :---: | :---: | :---: |
| **Archivos Lógicos Internos (ILF)** | 5 (x7 = 35) | 0 (x10 = 0) | 0 (x15 = 0) | **5** | **35 PF** |
| **Archivos de Interfaz Externa (EIF)** | 1 (x5 = 5) | 0 (x7 = 0) | 0 (x10 = 0) | **1** | **5 PF** |
| **Entradas Externas (EI)** | 13 (x3 = 39) | 10 (x4 = 40) | 1 (x6 = 6) | **24** | **85 PF** |
| **Salidas Externas (EO)** | 0 (x4 = 0) | 3 (x5 = 15) | 0 (x7 = 0) | **3** | **15 PF** |
| **Consultas Externas (EQ)** | 4 (x3 = 12) | 5 (x4 = 20) | 0 (x6 = 0) | **9** | **32 PF** |
| **TOTAL GENERAL NO AJUSTADO** | **23** | **18** | **1** | **42** | **172 UFP** |

> [!NOTE]
> **Detalle del conteo de Entradas Externas (EI):**
> * Complejidad Baja (13 funciones x 3 PF = 39 PF): TF-01, TF-02, TF-03, TF-06, TF-07, TF-08, TF-14, TF-17, TF-22, TF-23, TF-24, TF-25, TF-26, TF-27, TF-30, TF-31... Verifiquemos el desglose exacto:
>   - Baja: TF-01 (3), TF-02 (3), TF-03 (3), TF-06 (3), TF-07 (3), TF-08 (3), TF-14 (3), TF-17 (3), TF-22 (3), TF-23 (3), TF-24 (3), TF-25 (3), TF-26 (3), TF-27 (3), TF-30 (3), TF-31 (3) $\rightarrow$ 16 funciones x 3 = 48 PF.
>   - Media: TF-11 (4), TF-12 (4), TF-13 (4), TF-20 (4), TF-21 (4), TF-32 (4), TF-33 (4) $\rightarrow$ 7 funciones x 4 = 28 PF.
>   - Alta: TF-18 (6) $\rightarrow$ 1 función x 6 = 6 PF.
>   - Total EI: 16 (Baja) + 7 (Media) + 1 (Alta) = 24 funciones $\implies 48 + 28 + 6 = \mathbf{82 \text{ PF}}$.

Ajustando con el desglose exacto verificado:

| Tipo de Componente | Cantidad | Complejidad Baja | Complejidad Media | Complejidad Alta | Total Puntos (PF) |
| :--- | :---: | :---: | :---: | :---: | :---: |
| **ILF (Archivos Lógicos Internos)** | 5 | 5 x 7 = 35 | 0 x 10 = 0 | 0 x 15 = 0 | **35 PF** |
| **EIF (Archivos de Interfaz Externa)**| 1 | 1 x 5 = 5 | 0 x 7 = 0 | 0 x 10 = 0 | **5 PF** |
| **EI (Entradas Externas)** | 24 | 16 x 3 = 48 | 7 x 4 = 28 | 1 x 6 = 6 | **82 PF** |
| **EO (Salidas Externas)** | 3 | 0 x 4 = 0 | 3 x 5 = 15 | 0 x 7 = 0 | **15 PF** |
| **EQ (Consultas Externas)** | 9 | 4 x 3 = 12 | 5 x 4 = 20 | 0 x 6 = 0 | **32 PF** |
| **TOTAL NO AJUSTADO (UFP)** | **42** | **100 PF** | **63 PF** | **6 PF** | **169 UFP** |

---

### 7.2. Cálculo de los Puntos de Función Ajustados (AFP / PFA)

Utilizando la fórmula canónica de IFPUG CPM 4.2.1:

$$AFP = UFP \times VAF$$

Sustituyendo los valores calculados:

$$AFP = 169 \times 0.98 = \mathbf{165.62 \text{ Puntos de Función}}$$

Redondeando al entero más cercano según las directrices de estimación del estándar:

$$\mathbf{AFP \approx 166 \text{ Puntos de Función Ajustados (PFA)}}$$

---

### 7.3. Distribución Porcentual del Tamaño Funcional

```
Distribución del Tamaño Funcional por Tipo de Componente (Total: 169 UFP)
========================================================================
[================================================] EI:  82 PF (48.5%)
[=====================]                           ILF: 35 PF (20.7%)
[===================]                             EQ:  32 PF (18.9%)
[=========]                                       EO:  15 PF (8.9%)
[===]                                             EIF:  5 PF (3.0%)
```

* **Funcionalidad de Entrada de Datos y Reglas de Negocio (EI):** Representa el 48.5% del sistema, evidenciando un aplicativo con fuerte foco en la gestión transaccional, control atómico de inventario, validaciones de seguridad y orquestación de estados.
* **Funcionalidad de Persistencia Interna (ILF):** Representa el 20.7%, sustentado en un modelo de datos relacional normalizado con separación de identidades internas y públicas (Dual-ID), control de usuarios y ciclo de vida de préstamos.
* **Funcionalidad de Recuperación y Consulta (EQ + EO):** Representa el 27.8%, proveyendo auditoría, búsqueda dinámica con filtros combinados, visualización de historiales propios y notificaciones electrónicas automáticas.
* **Integración Externa (EIF):** Representa el 3.0%, vinculando metadatos bibliográficos mundiales provistos por la API de Google Books.

---

## 8. Conclusiones y Trazabilidad

1. **Cumplimiento del Estándar IFPUG CPM 4.2.1:**
   La medición se ha efectuado siguiendo rigurosamente las pautas de frontera, tipificación de componentes y matrices de complejidad de RET/DET y FTR/DET del manual oficial.
2. **Desglose Exhaustivo de Procesos:**
   Cada endpoint disponible en el código fuente Java Spring Boot fue catalogado y mapeado directamente a su proceso elemental correspondiente, diferenciando con precisión las operaciones de Alta, Modificación y Baja como Entradas Externas independientes.
3. **Inclusión de Entidades Locales e Integración Externa:**
   Se incorporaron exhaustivamente todas las tablas y entidades relacionales de la base de datos dentro de los 5 Archivos Lógicos Internos (ILF), y se modeló la integración con la API de Google Books como un Archivo de Interfaz Externa (EIF).
4. **Justificación Objetiva del Factor de Ajuste:**
   Las 14 Características Generales del Sistema fueron evaluadas analizando puntualmente el código fuente y las especificaciones arquitectónicas del proyecto, arrojando un Grado Total de Influencia $TDI = 33$ y un $VAF = 0.98$, lo que sitúa la medición final en **166 PFA**.
