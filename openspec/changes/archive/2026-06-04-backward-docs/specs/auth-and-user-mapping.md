# Spec: Authentication and User Mapping

## EXISTING Requirements

### Requirement: OAuth2/OIDC Login via Microsoft Entra ID

#### Scenario: Unauthenticated access

- **WHEN** an unauthenticated user accesses any route other than `/css/**`, `/js/**`, `/webjars/**`, or `/actuator/health/**`
- **THEN** they are redirected to the Microsoft Entra ID login page
- **AND** after successful authentication they are redirected back to the originally requested URL

#### Scenario: Session persistence

- **WHEN** a user successfully authenticates
- **THEN** the session is stored in MongoDB (collection `spring:session:sessions`)
- **AND** the session remains valid for 7 days (604800 seconds)
- **AND** the session survives application pod restarts

---

### Requirement: Email → App-User Mapping

The application has two named app users (`Victor` and `Andre`). A first-time login triggers a mapping of the OIDC email to one of these names.

#### Scenario: First login — mapping required

- **WHEN** an authenticated user's OIDC email has no existing mapping in `user_email_mappings`
- **THEN** `userMappingRequired = true` is injected into every Thymeleaf model
- **AND** a non-dismissible Bootstrap modal appears prompting the user to select their app username

#### Scenario: Mapping submission

- **WHEN** a POST is sent to `/api/user/map` with `appUser=Victor` or `appUser=Andre`
- **THEN** a `UserEmailMapping` document is saved with `_id = lowercase(email)` and the chosen `appUser`
- **AND** the modal no longer appears on subsequent requests

#### Scenario: Invalid app user rejection

- **WHEN** a POST is sent to `/api/user/map` with an `appUser` value other than `Victor` or `Andre`
- **THEN** a 400 Bad Request response is returned

---

### Requirement: Production Lock

#### Scenario: Prod mapping immutable

- **WHEN** the application is running in production (MongoDB URI does NOT contain `mongodb-service.treasury.svc.cluster.local`)
- **AND** an existing mapping for the authenticated email already exists
- **THEN** a POST to `/api/user/map` returns a 409 Conflict without overwriting the mapping

#### Scenario: Dev allows overwrite

- **WHEN** the application is running in development (MongoDB URI contains `mongodb-service.treasury.svc.cluster.local`)
- **THEN** an existing email mapping can be overwritten by a new POST to `/api/user/map`

---

### Requirement: Global Model Attributes

#### Scenario: Injected attributes

- **WHEN** any Thymeleaf page is rendered
- **THEN** the following attributes are available in all templates:
  - `appVersion`: from `META-INF/app.properties` (falls back to `"dev"`)
  - `buildTimestamp`: from `META-INF/app.properties` (falls back to `"unknown"`)
  - `mongoHost`: extracted host portion of the MongoDB URI
  - `isProd`: boolean, true when not on the dev MongoDB host
  - `currentUri`: the request URI (for active nav highlighting)
  - `currentAppUser`: the app username for the authenticated email, or `null` if unmapped
  - `userMappingRequired`: true when `currentAppUser` is null

---

### Requirement: CSRF Protection

#### Scenario: API endpoints

- **WHEN** a JavaScript `fetch()` call is made to any `/api/**` endpoint
- **THEN** CSRF tokens are NOT required (CSRF is disabled for `/api/**`)

#### Scenario: Thymeleaf form endpoints

- **WHEN** a form POST is submitted to any non-API endpoint
- **THEN** a valid CSRF token must be included in the request
