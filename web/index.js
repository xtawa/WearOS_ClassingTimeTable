// AirHost Worker for Classing (Backend replacement - Round 1)
// Implements core health, authentication, account status, and official cloud timetable sync (v2).

async function sha256(str) {
  const buf = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(str));
  return Array.from(new Uint8Array(buf))
    .map((b) => b.toString(16).padStart(2, "0"))
    .join("");
}

function parseIfMatch(header) {
  if (!header) return null;
  let trimmed = header.trim();
  if (trimmed.startsWith('W/')) {
    trimmed = trimmed.slice(2).trim();
  }
  if (trimmed.startsWith('"') && trimmed.endsWith('"')) {
    const num = parseInt(trimmed.slice(1, -1), 10);
    return isNaN(num) ? null : num;
  }
  const num = parseInt(trimmed, 10);
  return isNaN(num) ? null : num;
}

const CORS_HEADERS = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Methods": "GET, POST, PUT, PATCH, DELETE, OPTIONS",
  "Access-Control-Allow-Headers": "*",
  "Access-Control-Expose-Headers": "ETag, Content-Type, Last-Modified",
};

function jsonResponse(data, status = 200, extraHeaders = {}) {
  return new Response(JSON.stringify(data), {
    status,
    headers: {
      "Content-Type": "application/json; charset=utf-8",
      ...CORS_HEADERS,
      ...extraHeaders,
    },
  });
}

function emptyCloudDocumentV2() {
  return JSON.stringify({
    format: "classing_cloud_sync_v2",
    updatedAt: 0,
    records: {},
    changes: [],
    devices: [],
  });
}

export default {
  async fetch(request, env) {
    if (request.method === "OPTIONS") {
      return new Response(null, { status: 204, headers: CORS_HEADERS });
    }

    const url = new URL(request.url);

    // Initialize idempotent tables
    await env.DB.exec(
      "CREATE TABLE IF NOT EXISTS cloud_documents (" +
        "user_id TEXT PRIMARY KEY, version INTEGER NOT NULL DEFAULT 1, " +
        "payload TEXT NOT NULL, updated_at INTEGER NOT NULL);" +
      "CREATE TABLE IF NOT EXISTS users (" +
        "id TEXT PRIMARY KEY, username TEXT NOT NULL, email TEXT NOT NULL, " +
        "password_hash TEXT NOT NULL, salt TEXT NOT NULL, created_at INTEGER NOT NULL);" +
      "CREATE TABLE IF NOT EXISTS sessions (" +
        "token TEXT PRIMARY KEY, refresh_token TEXT NOT NULL, user_id TEXT NOT NULL, " +
        "expires_at INTEGER NOT NULL, refresh_expires_at INTEGER NOT NULL, created_at INTEGER NOT NULL);"
    );

    // Authenticate user
    async function getAuthUser() {
      // 1. Platform-managed session
      if (request.ctx && request.ctx.user) {
        return {
          id: request.ctx.user.id,
          username: request.ctx.user.name || (request.ctx.user.email ? request.ctx.user.email.split("@")[0] : request.ctx.user.id),
          email: request.ctx.user.email || "",
          emailVerified: !!request.ctx.user.emailVerified,
        };
      }

      // 2. Bearer token in Authorization header
      const authHeader = request.headers.get("Authorization");
      if (authHeader && authHeader.startsWith("Bearer ")) {
        const token = authHeader.slice(7).trim();
        const now = Date.now();
        const sessionRow = await env.DB.prepare(
          "SELECT user_id, expires_at FROM sessions WHERE token = ? AND expires_at > ?"
        ).bind(token, now).first();

        if (sessionRow) {
          const userRow = await env.DB.prepare(
            "SELECT id, username, email FROM users WHERE id = ?"
          ).bind(sessionRow.user_id).first();

          if (userRow) {
            return {
              id: userRow.id,
              username: userRow.username,
              email: userRow.email,
              emailVerified: true,
            };
          }
        }
      }

      return null;
    }

    // Health check
    if (url.pathname === "/" || url.pathname === "/api/v1/health") {
      return jsonResponse({
        status: "ok",
        service: "classing-airhost",
        version: "1.0.0",
        provider: "AIRHOST",
        timestamp: Date.now(),
      });
    }

    // Cloud status and config
    if (url.pathname === "/api/v1/cloud/ping") {
      return jsonResponse({
        status: "ok",
        provider: "OFFICIAL",
        canSyncSettings: true,
        canSyncTimetable: true,
      });
    }

    if (url.pathname === "/api/v1/cloud/config") {
      return jsonResponse({
        provider: "OFFICIAL",
        maxDocumentBytes: 10485760,
        etagRequired: true,
        idempotencyKeyRecommended: true,
        syncScopes: ["TIMETABLE", "MOBILE_SETTINGS", "WEAR_SETTINGS"],
      });
    }

    // Registration config (for client login view)
    if (url.pathname === "/api/v1/auth/registration/config") {
      return jsonResponse({
        turnstileRequired: false,
        turnstileSiteKey: "",
        legalAgreementUrls: {
          privacyPolicy: "https://lyxyy.notion.site/classing-user-policy",
          termsOfService: "https://www.apple.com/legal/internet-services/itunes/dev/stdeula/",
          crossBorderTransfer: "https://lyxyy.notion.site/classing-user-policy",
        },
      });
    }

    // Auth Login
    if (url.pathname === "/api/v1/auth/login" && request.method === "POST") {
      try {
        const body = await request.json();
        const identifier = (body.identifier || "").trim();
        const password = body.password || "";

        if (!identifier || !password) {
          return jsonResponse({ error: true, code: "INVALID_CREDENTIALS", message: "请输入账号和密码" }, 400);
        }

        // Look up user by email or username
        let user = await env.DB.prepare(
          "SELECT id, username, email, password_hash, salt FROM users WHERE email = ? OR username = ?"
        ).bind(identifier, identifier).first();

        const now = Date.now();

        if (!user) {
          // Auto-provision test/dev user on first login on AirHost
          const salt = crypto.randomUUID().slice(0, 8);
          const hash = await sha256(password + ":" + salt);
          const userId = "usr_" + crypto.randomUUID().replace(/-/g, "").slice(0, 16);
          const username = identifier.includes("@") ? identifier.split("@")[0] : identifier;
          const email = identifier.includes("@") ? identifier : `${identifier}@classing.local`;

          await env.DB.prepare(
            "INSERT INTO users (id, username, email, password_hash, salt, created_at) VALUES (?, ?, ?, ?, ?, ?)"
          ).bind(userId, username, email, hash, salt, now).run();

          user = { id: userId, username, email, password_hash: hash, salt };
        } else {
          // Verify password
          const computedHash = await sha256(password + ":" + user.salt);
          if (computedHash !== user.password_hash) {
            return jsonResponse({ error: true, code: "INVALID_PASSWORD", message: "密码错误" }, 401);
          }
        }

        // Create session
        const accessToken = "cls_at_" + crypto.randomUUID().replace(/-/g, "");
        const refreshToken = "cls_rt_" + crypto.randomUUID().replace(/-/g, "");
        const accessExpiresAt = now + 7 * 86400 * 1000;
        const refreshExpiresAt = now + 30 * 86400 * 1000;

        await env.DB.prepare(
          "INSERT INTO sessions (token, refresh_token, user_id, expires_at, refresh_expires_at, created_at) " +
          "VALUES (?, ?, ?, ?, ?, ?)"
        ).bind(accessToken, refreshToken, user.id, accessExpiresAt, refreshExpiresAt, now).run();

        return jsonResponse({
          session: {
            accessToken,
            refreshToken,
            accessExpiresAt,
            refreshExpiresAt,
            account: {
              id: user.id,
              userId: user.id,
              username: user.username,
              email: user.email,
            },
          },
        });
      } catch (err) {
        return jsonResponse({ error: true, code: "INTERNAL_ERROR", message: err.message }, 500);
      }
    }

    // Auth Refresh
    if (url.pathname === "/api/v1/auth/refresh" && request.method === "POST") {
      try {
        const body = await request.json();
        const refreshToken = body.refreshToken;
        const now = Date.now();

        const session = await env.DB.prepare(
          "SELECT user_id FROM sessions WHERE refresh_token = ? AND refresh_expires_at > ?"
        ).bind(refreshToken, now).first();

        if (!session) {
          return jsonResponse({ error: true, code: "INVALID_REFRESH_TOKEN", message: "刷新凭据无效或已过期" }, 401);
        }

        const user = await env.DB.prepare("SELECT id, username, email FROM users WHERE id = ?").bind(session.user_id).first();
        if (!user) {
          return jsonResponse({ error: true, code: "USER_NOT_FOUND", message: "用户不存在" }, 401);
        }

        const newAccessToken = "cls_at_" + crypto.randomUUID().replace(/-/g, "");
        const accessExpiresAt = now + 7 * 86400 * 1000;

        await env.DB.prepare(
          "UPDATE sessions SET token = ?, expires_at = ? WHERE refresh_token = ?"
        ).bind(newAccessToken, accessExpiresAt, refreshToken).run();

        return jsonResponse({
          session: {
            accessToken: newAccessToken,
            refreshToken,
            accessExpiresAt,
            refreshExpiresAt: now + 30 * 86400 * 1000,
            account: {
              id: user.id,
              userId: user.id,
              username: user.username,
              email: user.email,
            },
          },
        });
      } catch (err) {
        return jsonResponse({ error: true, code: "INTERNAL_ERROR", message: err.message }, 500);
      }
    }

    // Auth Logout
    if (url.pathname === "/api/v1/auth/logout" && request.method === "POST") {
      try {
        const body = await request.json();
        if (body.refreshToken) {
          await env.DB.prepare("DELETE FROM sessions WHERE refresh_token = ?").bind(body.refreshToken).run();
        }
        return jsonResponse({ ok: true });
      } catch (_) {
        return jsonResponse({ ok: true });
      }
    }

    // Account me
    if (url.pathname === "/api/v1/account/me") {
      const user = await getAuthUser();
      if (!user) {
        return jsonResponse({ error: true, code: "sign_in_required", message: "需要登录" }, 401);
      }
      return jsonResponse({
        account: {
          id: user.id,
          userId: user.id,
          username: user.username,
          email: user.email,
          emailVerified: user.emailVerified,
          role: "USER",
          createdAt: Date.now(),
        },
      });
    }

    // Membership status (grant cloudSync permissions for AirHost users)
    if (url.pathname === "/api/v1/membership/status") {
      const user = await getAuthUser();
      if (!user) {
        return jsonResponse({ error: true, code: "sign_in_required", message: "需要登录" }, 401);
      }
      return jsonResponse({
        membership: {
          accountType: "PRO",
          tier: "PRO",
          access: {
            cloudSync: true,
            byok: true,
          },
          expiresAt: 4102444800000,
        },
      });
    }

    // Cloud Official Document Sync
    if (url.pathname === "/api/v1/cloud/official/document") {
      const user = await getAuthUser();
      if (!user) {
        return jsonResponse({ error: true, code: "sign_in_required", message: "需要登录" }, 401);
      }

      if (request.method === "GET") {
        const row = await env.DB.prepare(
          "SELECT version, payload, updated_at FROM cloud_documents WHERE user_id = ?"
        ).bind(user.id).first();

        if (!row) {
          return new Response(emptyCloudDocumentV2(), {
            status: 200,
            headers: {
              "Content-Type": "application/json; charset=utf-8",
              "ETag": '"0"',
              ...CORS_HEADERS,
            },
          });
        }

        const etag = `"${row.version}"`;
        if (request.headers.get("If-None-Match") === etag) {
          return new Response(null, { status: 304, headers: { "ETag": etag, ...CORS_HEADERS } });
        }

        return new Response(row.payload, {
          status: 200,
          headers: {
            "Content-Type": "application/json; charset=utf-8",
            "ETag": etag,
            "Last-Modified": new Date(row.updated_at).toUTCString(),
            ...CORS_HEADERS,
          },
        });
      }

      if (request.method === "PUT") {
        const expectedVersion = parseIfMatch(request.headers.get("If-Match"));
        if (expectedVersion === null) {
          return jsonResponse(
            { error: true, code: "OFFICIAL_CLOUD_PRECONDITION_REQUIRED", message: "If-Match is required" },
            428
          );
        }

        const row = await env.DB.prepare(
          "SELECT version FROM cloud_documents WHERE user_id = ?"
        ).bind(user.id).first();

        const currentVersion = row ? row.version : 0;
        if (expectedVersion !== currentVersion) {
          return jsonResponse(
            { error: true, code: "OFFICIAL_CLOUD_VERSION_CONFLICT", message: "cloud document has changed" },
            412
          );
        }

        const payloadText = await request.text();
        try {
          const parsed = JSON.parse(payloadText);
          if (parsed.format !== "classing_cloud_sync_v2") {
            return jsonResponse({ error: true, code: "INVALID_DOCUMENT_FORMAT", message: "云文档格式不受支持" }, 400);
          }
        } catch (_) {
          return jsonResponse({ error: true, code: "INVALID_JSON", message: "文档不是有效 JSON" }, 400);
        }

        const newVersion = currentVersion + 1;
        const now = Date.now();

        await env.DB.prepare(
          "INSERT INTO cloud_documents (user_id, version, payload, updated_at) VALUES (?, ?, ?, ?) " +
          "ON CONFLICT(user_id) DO UPDATE SET version = excluded.version, payload = excluded.payload, updated_at = excluded.updated_at"
        ).bind(user.id, newVersion, payloadText, now).run();

        return new Response(JSON.stringify({ success: true, version: newVersion }), {
          status: 200,
          headers: {
            "Content-Type": "application/json; charset=utf-8",
            "ETag": `"${newVersion}"`,
            ...CORS_HEADERS,
          },
        });
      }

      return jsonResponse({ error: true, code: "METHOD_NOT_ALLOWED" }, 405);
    }

    // Daily briefings placeholder
    if (url.pathname === "/api/v1/briefings/daily") {
      return jsonResponse({
        briefing: {
          enabled: false,
          channel: "APP_NOTIFICATION",
          time: "07:30",
          timezone: "Asia/Shanghai",
        },
      });
    }

    return jsonResponse({ error: true, code: "NOT_FOUND", message: "AirHost 端点未实现或不存在" }, 404);
  },

  async deleteUser(user, env) {
    await env.DB.prepare("DELETE FROM cloud_documents WHERE user_id = ?").bind(user.id).run();
    await env.DB.prepare("DELETE FROM sessions WHERE user_id = ?").bind(user.id).run();
    await env.DB.prepare("DELETE FROM users WHERE id = ?").bind(user.id).run();
  },
};
