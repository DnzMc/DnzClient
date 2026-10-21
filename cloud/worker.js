// DNZ Cloud: keeps a player's DNZ Client settings (key bindings, DNZ menu and HUD settings) so they follow
// the player to another computer.

const MAX_SIZE = 256 * 1024;
const TOKEN_TIME = 60 * 60; // seconds
const CHALLENGE_TIME = 120; // seconds
/** Mojang player certificate keys (api.minecraftservices.com/publickeys → playerCertificateKeys). */
const MOJANG_KEYS = [
	"MIICIjANBgkqhkiG9w0BAQEFAAOCAg8AMIICCgKCAgEAylB4B6m5lz7jwrcFz6Fd/fnfUhcvlxsTSn5kIK/2aGG1C3kMy4VjhwlxF6BFUSnfxhNswPjh3ZitkBxEAFY25uzkJFRwHwVA9mdwjashXILtR6OqdLXXFVyUPIURLOSWqGNBtb08EN5fMnG8iFLgEJIBMxs9BvF3s3/FhuHyPKiVTZmXY0WY4ZyYqvoKR+XjaTRPPvBsDa4WI2u1zxXMeHlodT3lnCzVvyOYBLXL6CJgByuOxccJ8hnXfF9yY4F0aeL080Jz/3+EBNG8RO4ByhtBf4Ny8NQ6stWsjfeUIvH7bU/4zCYcYOq4WrInXHqS8qruDmIl7P5XXGcabuzQstPf/h2CRAUpP/PlHXcMlvewjmGU6MfDK+lifScNYwjPxRo4nKTGFZf/0aqHCh/EAsQyLKrOIYRE0lDG3bzBh8ogIMLAugsAfBb6M3mqCqKaTMAf/VAjh5FFJnjS+7bE+bZEV0qwax1CEoPPJL1fIQjOS8zj086gjpGRCtSy9+bTPTfTR/SJ+VUB5G2IeCItkNHpJX2ygojFZ9n5Fnj7R9ZnOM+L8nyIjPu3aePvtcrXlyLhH/hvOfIOjPxOlqW+O5QwSFP4OEcyLAUgDdUgyW36Z5mB285uKW/ighzZsOTevVUG2QwDItObIV6i8RCxFbN2oDHyPaO5j1tTaBNyVt8CAwEAAQ==",
	"MIICIjANBgkqhkiG9w0BAQEFAAOCAg8AMIICCgKCAgEAt4t9NPuu7cktclnaH7eZj0omkLcJHeLz5MKsyJEntHZ0INtuBjSSul3Pp3pBeJN8k3ADdcdBLUN90bcAi7WsQqTx3Ft363q3W7TbM8j2iTEdp/0uVspoRt/DP1tkaWFs/w2WwUv9jbVoBUzfUc4pSTIxRwdjmqjZQfvjwKNDbOx3IhP2H0WXodbISejPi1wBZqNW4m1rnZAXp/EpUguxA8mobCa4vUCBkyFDyXdl69/wUSJHyCPmgcMJ364OlAhIqtwVPShBZObvrK/f0BYk6ShJD3N7TFDatSYsIIdcTKRknaIm91s+EsMrdB9U4Yw+ZJ/pyCB4S3vk8zfDCnb0DWIxYH3/EMzaxl77djmTmMzi/JDITup5z3jfWtRZmrAhU2/+W5IO5hEpo3/bCS9PXIY5xb41Lmp2ZO8dXKtyD66Chchy0W129n8vPl2GIruOdrxsjZAHnneyAb9jm0uaGaphwnEnuecX/qgHY6ZMtayvLLsPst8PO6R1vufMy8WqjK+j7LnC1krL7CPDg0NEhyQTmw5l+NCNjSlvB1juM9V4PARg0bYCOkGXm7ydRCjSSH8CJXZpwnd5cBB5WKAX3KPzutRgMi/LFwNSMZzFuUyXaYOZPpD259yqph1LmGqegEdDriACVU+dVEONFMm8eIuBofe7ljmsAFKW9BINwK0CAwEAAQ==",
];

export default {
	async fetch(request, env) {
		const url = new URL(request.url);
		try {
			if (url.pathname.startsWith("/cf/")) {
				return await curseforge(request, url, env);
			}
			if (url.pathname === "/presence" && request.method === "POST") {
				return await presence(request, env);
			}
			if (url.pathname === "/presence" && request.method === "GET") {
				return json(await presenceCounts(env));
			}
			if (url.pathname === "/admin" && request.method === "GET") {
				return await adminPlayers(request, env);
			}
			if (url.pathname === "/online" && request.method === "POST") {
				return await online(request, env);
			}
			if (url.pathname === "/account/profile") {
				return await accountProfile(request, env);
			}
			if (url.pathname === "/workshop/library") {
				return await workshopLibrary(request, env);
			}
			if (url.pathname === "/challenge" && request.method === "GET") {
				const nonce = b64url(new TextEncoder().encode(JSON.stringify({ t: Math.floor(Date.now() / 1000), r: b64url(crypto.getRandomValues(new Uint8Array(16))) })));
				return json({ challenge: `${nonce}.${await sign(nonce, env.SECRET)}` });
			}
			if (url.pathname === "/auth" && request.method === "POST") {
				return await auth(request, env);
			}
			if (url.pathname === "/settings") {
				const uuid = await session(request, env);
				if (!uuid) {
					return json({ error: "not signed in" }, 401);
				}
				const key = `settings/${uuid}.json`;
				if (request.method === "GET") {
					const object = await env.BUCKET.get(key);
					return object ? new Response(object.body, { headers: { "Content-Type": "application/json" } }) : json({ error: "none" }, 404);
				}
				if (request.method === "PUT") {
					const body = await request.text();
					if (body.length > MAX_SIZE) {
						return json({ error: "too big" }, 413);
					}
					JSON.parse(body); // only JSON is stored
					await env.BUCKET.put(key, body, { httpMetadata: { contentType: "application/json" } });
					return json({ ok: true });
				}
				if (request.method === "DELETE") {
					await env.BUCKET.delete(key);
					return json({ ok: true });
				}
			}
			return json({ error: "not found" }, 404);
		} catch (e) {
			// A missing setting is the owner's to fix; say which one instead of "bad request".
			if (String(e && e.message).includes("is not set")) {
				return json({ error: e.message }, 503);
			}
			return json({ error: "bad request" }, 400);
		}
	},
};

/** CurseForge requests the launcher may make (anything else is refused). */
const CF_GET = [/^\/v1\/mods\/search$/, /^\/v1\/mods\/\d{1,9}$/, /^\/v1\/mods\/\d{1,9}\/description$/, /^\/v1\/mods\/\d{1,9}\/files$/];
const CF_CACHE = 300; // seconds

async function curseforge(request, url, env) {
	const path = url.pathname.slice(3); // "/cf/v1/..." → "/v1/..."
	const featured = path === "/v1/mods/featured" && request.method === "POST";
	if (!featured && (request.method !== "GET" || !CF_GET.some((r) => r.test(path)))) {
		return json({ error: "not allowed" }, 403);
	}
	if (path === "/v1/mods/search" && url.searchParams.get("gameId") !== "432") {
		return json({ error: "minecraft only" }, 403);
	}
	if (url.search.length > 600) {
		return json({ error: "too long" }, 414);
	}
	const headers = { "x-api-key": env.CURSEFORGE_KEY, Accept: "application/json" };
	if (featured) {
		// only Minecraft's lists; the launcher's body is not passed on
		headers["Content-Type"] = "application/json";
		const r = await fetch("https://api.curseforge.com/v1/mods/featured", { method: "POST", headers, body: JSON.stringify({ gameId: 432, excludedModIds: [] }) });
		return new Response(r.body, { status: r.status, headers: { "Content-Type": "application/json" } });
	}
	// the same question within a few minutes is answered from Cloudflare's cache (CurseForge is asked once)
	const cache = caches.default;
	const cacheKey = new Request(url.toString(), { method: "GET" });
	const hit = await cache.match(cacheKey);
	if (hit) {
		return hit;
	}
	const r = await fetch("https://api.curseforge.com" + path + url.search, { headers });
	const response = new Response(r.body, { status: r.status, headers: { "Content-Type": "application/json", "Cache-Control": `public, max-age=${CF_CACHE}` } });
	if (r.ok) {
		await cache.put(cacheKey, response.clone());
	}
	return response;
}

/**
 * {uuid, expiresAt, publicKey, keySignature, challenge, signature} → a session token for one hour, when
 * Mojang signed the player's key for that uuid and that key signed the challenge.
 */
async function auth(request, env) {
	const b = await request.json();
	if (!/^[0-9a-f]{32}$/.test(b.uuid) || typeof b.expiresAt !== "number" || b.expiresAt < Date.now()) {
		return json({ error: "bad request" }, 400);
	}
	// the challenge must come from this worker and be fresh
	const [nonce, mac] = String(b.challenge).split(".");
	if (!nonce || mac !== (await sign(nonce, env.SECRET))
		|| JSON.parse(new TextDecoder().decode(fromB64url(nonce))).t < Date.now() / 1000 - CHALLENGE_TIME) {
		return json({ error: "bad challenge" }, 401);
	}
	const publicKey = fromB64(b.publicKey);
	// what Mojang signed: uuid (16 bytes) + expiry in milliseconds (8 bytes) + the player's public key
	const payload = new Uint8Array(24 + publicKey.length);
	for (let i = 0; i < 16; i++) {
		payload[i] = parseInt(b.uuid.substr(i * 2, 2), 16);
	}
	new DataView(payload.buffer).setBigInt64(16, BigInt(b.expiresAt));
	payload.set(publicKey, 24);
	let mojangSigned = false;
	for (const k of MOJANG_KEYS) {
		const key = await crypto.subtle.importKey("spki", fromB64(k), { name: "RSASSA-PKCS1-v1_5", hash: "SHA-1" }, false, ["verify"]);
		if (await crypto.subtle.verify("RSASSA-PKCS1-v1_5", key, fromB64(b.keySignature), payload)) {
			mojangSigned = true;
			break;
		}
	}
	if (!mojangSigned) {
		return json({ error: "not verified" }, 401);
	}
	const playerKey = await crypto.subtle.importKey("spki", publicKey, { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" }, false, ["verify"]);
	if (!(await crypto.subtle.verify("RSASSA-PKCS1-v1_5", playerKey, fromB64(b.signature), new TextEncoder().encode(b.challenge)))) {
		return json({ error: "not verified" }, 401);
	}
	const token = b64url(new TextEncoder().encode(JSON.stringify({ uuid: b.uuid, exp: Math.floor(Date.now() / 1000) + TOKEN_TIME })));
	return json({ token: `${token}.${await sign(token, env.SECRET)}`, uuid: b.uuid });
}

/** The player id of a valid "Authorization: Bearer <token>", or null. */
async function session(request, env) {
	const header = request.headers.get("Authorization") || "";
	const [payload, signature] = header.replace(/^Bearer /, "").split(".");
	if (!payload || !signature || signature !== (await sign(payload, env.SECRET))) {
		return null;
	}
	const data = JSON.parse(new TextDecoder().decode(fromB64url(payload)));
	return data.exp > Date.now() / 1000 && /^[0-9a-f]{32}$/.test(data.uuid) ? data.uuid : null;
}

async function sign(text, secret) {
	if (!secret) {
		throw new Error("SECRET is not set (Worker → Settings → Variables and Secrets)");
	}
	const key = await crypto.subtle.importKey("raw", new TextEncoder().encode(secret), { name: "HMAC", hash: "SHA-256" }, false, ["sign"]);
	return b64url(new Uint8Array(await crypto.subtle.sign("HMAC", key, new TextEncoder().encode(text))));
}

function b64url(bytes) {
	return btoa(String.fromCharCode(...bytes)).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
}

function fromB64(text) {
	return Uint8Array.from(atob(text), (c) => c.charCodeAt(0));
}

function fromB64url(text) {
	return Uint8Array.from(atob(text.replace(/-/g, "+").replace(/_/g, "/")), (c) => c.charCodeAt(0));
}

function json(data, status = 200) {
	return new Response(JSON.stringify(data), { status, headers: { "Content-Type": "application/json" } });
}

// ------------------------------------------------------------------ DNZ Workshop library
// The Workshop items a player added (theme ids), the same list on dnzclient.com and in DNZ Launcher.

const MS_ISSUER = "https://login.microsoftonline.com/9188040d-6c67-4c5b-b112-36a304b66dad/v2.0"; // personal accounts
const MS_KEYS = "https://login.microsoftonline.com/consumers/discovery/v2.0/keys";
const WORKSHOP_ORIGINS = ["https://dnzclient.com", "https://www.dnzclient.com", "http://localhost:8765"];
let msKeys = null; // { at, keys }

async function workshopLibrary(request, env) {
	const origin = request.headers.get("Origin") || "";
	const cors = {
		"Access-Control-Allow-Origin": WORKSHOP_ORIGINS.includes(origin) ? origin : WORKSHOP_ORIGINS[0],
		"Access-Control-Allow-Methods": "GET, PUT, OPTIONS",
		"Access-Control-Allow-Headers": "Authorization, Content-Type",
		Vary: "Origin",
	};
	const reply = (data, status = 200) => new Response(JSON.stringify(data), { status, headers: { ...cors, "Content-Type": "application/json" } });
	if (request.method === "OPTIONS") {
		return new Response(null, { status: 204, headers: cors });
	}
	const id = await webAccount(request, env);
	if (!id) {
		return reply({ error: "not signed in" }, 401);
	}
	const key = `workshop/library/${id}.json`;
	if (request.method === "GET") {
		const object = await env.BUCKET.get(key);
		return reply(object ? await object.json() : { items: [] });
	}
	if (request.method === "PUT") {
		const body = await request.json();
		const items = Array.isArray(body.items) ? body.items.filter((x) => typeof x === "string" && /^[A-Za-z0-9_-]{1,40}$/.test(x)) : null;
		if (!items || items.length > 300) {
			return reply({ error: "bad list" }, 400);
		}
		const data = { items: [...new Set(items)], updated: Date.now() };
		await env.BUCKET.put(key, JSON.stringify(data), { httpMetadata: { contentType: "application/json" } });
		return reply(data);
	}
	return reply({ error: "not allowed" }, 405);
}

/** The Microsoft account id ("oid") of a valid ID token in the Authorization header, or null. */
async function microsoftAccount(request, env) {
	const claims = await microsoftClaims(request, env);
	return claims ? String(claims.oid).replace(/[^A-Za-z0-9-]/g, "") : null;
}

/** The claims of a valid Microsoft ID token (DNZ Launcher or the website), or null. */
async function microsoftClaims(request, env) {
	const token = (request.headers.get("Authorization") || "").replace(/^Bearer\s+/i, "");
	const parts = token.split(".");
	if (parts.length !== 3) {
		return null;
	}
	const header = JSON.parse(new TextDecoder().decode(fromB64url(parts[0])));
	const claims = JSON.parse(new TextDecoder().decode(fromB64url(parts[1])));
	const allowed = String(env.WORKSHOP_CLIENT_IDS || "").split(",").map((s) => s.trim()).filter(Boolean);
	const now = Math.floor(Date.now() / 1000);
	if (header.alg !== "RS256" || claims.iss !== MS_ISSUER || !allowed.includes(claims.aud) || !(claims.exp > now) || (claims.nbf && claims.nbf > now + 300) || !claims.oid) {
		return null;
	}
	const jwk = await microsoftKey(header.kid);
	if (!jwk) {
		return null;
	}
	const key = await crypto.subtle.importKey("jwk", { kty: "RSA", n: jwk.n, e: jwk.e }, { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" }, false, ["verify"]);
	const ok = await crypto.subtle.verify("RSASSA-PKCS1-v1_5", key, fromB64url(parts[2]), new TextEncoder().encode(`${parts[0]}.${parts[1]}`));
	return ok ? claims : null;
}

/**
 * Who signed in on the website or in DNZ Launcher: a Microsoft account id ("oid", as before) or "g-" + a
 * Google account id.
 */
async function webAccount(request, env) {
	return (await microsoftAccount(request, env)) || (await googleAccount(request, env));
}

const GOOGLE_KEYS = "https://www.googleapis.com/oauth2/v3/certs";
let googleKeys = null; // { at, keys }

/** "g-" + the Google account id ("sub") of a valid Google ID token for dnzclient.com, or null. */
async function googleAccount(request, env) {
	const token = (request.headers.get("Authorization") || "").replace(/^Bearer\s+/i, "");
	const parts = token.split(".");
	if (parts.length !== 3 || !env.GOOGLE_CLIENT_ID) {
		return null;
	}
	const header = JSON.parse(new TextDecoder().decode(fromB64url(parts[0])));
	const claims = JSON.parse(new TextDecoder().decode(fromB64url(parts[1])));
	const now = Math.floor(Date.now() / 1000);
	if (header.alg !== "RS256" || !["accounts.google.com", "https://accounts.google.com"].includes(claims.iss)
		|| claims.aud !== env.GOOGLE_CLIENT_ID || !(claims.exp > now) || !claims.sub) {
		return null;
	}
	if (!googleKeys || Date.now() - googleKeys.at > 3600_000 || !googleKeys.keys.some((k) => k.kid === header.kid)) {
		googleKeys = { at: Date.now(), keys: (await (await fetch(GOOGLE_KEYS)).json()).keys || [] };
	}
	const jwk = googleKeys.keys.find((k) => k.kid === header.kid);
	if (!jwk) {
		return null;
	}
	const key = await crypto.subtle.importKey("jwk", { kty: "RSA", n: jwk.n, e: jwk.e }, { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" }, false, ["verify"]);
	const ok = await crypto.subtle.verify("RSASSA-PKCS1-v1_5", key, fromB64url(parts[2]), new TextEncoder().encode(`${parts[0]}.${parts[1]}`));
	return ok ? "g-" + String(claims.sub).replace(/[^0-9]/g, "") : null;
}

// ------------------------------------------------------------------ dnzclient.com account profile
// The username and profile picture a player picked on dnzclient.com, so they come back on every sign-in and on every
// device. Stored in BUCKET as account/profile/<account id>.json. Nothing else about the player is kept.

const PROFILE_PICTURE_MAX = 200 * 1024; // characters of the picture's data URL

async function accountProfile(request, env) {
	const origin = request.headers.get("Origin") || "";
	const cors = {
		"Access-Control-Allow-Origin": WORKSHOP_ORIGINS.includes(origin) ? origin : WORKSHOP_ORIGINS[0],
		"Access-Control-Allow-Methods": "GET, PUT, OPTIONS",
		"Access-Control-Allow-Headers": "Authorization, Content-Type",
		Vary: "Origin",
	};
	const reply = (data, status = 200) => new Response(JSON.stringify(data), { status, headers: { ...cors, "Content-Type": "application/json" } });
	if (request.method === "OPTIONS") {
		return new Response(null, { status: 204, headers: cors });
	}
	const id = await webAccount(request, env);
	if (!id) {
		return reply({ error: "not signed in" }, 401);
	}
	const key = `account/profile/${id}.json`;
	if (request.method === "GET") {
		const object = await env.BUCKET.get(key);
		return reply(object ? await object.json() : {});
	}
	if (request.method === "PUT") {
		const text = await request.text();
		if (text.length > PROFILE_PICTURE_MAX + 2000) {
			return reply({ error: "too big" }, 413);
		}
		const body = JSON.parse(text);
		const name = typeof body.name === "string" && /^[A-Za-z0-9_]{3,16}$/.test(body.name) ? body.name : null;
		const avatar = body.avatar === null ? null
			: typeof body.avatar === "string" && /^data:image\/(png|jpeg|webp);base64,[A-Za-z0-9+/=]+$/.test(body.avatar) && body.avatar.length <= PROFILE_PICTURE_MAX ? body.avatar
			: undefined;
		if (!name || avatar === undefined) {
			return reply({ error: "bad profile" }, 400);
		}
		const data = { name, avatar, updated: Date.now() };
		await env.BUCKET.put(key, JSON.stringify(data), { httpMetadata: { contentType: "application/json" } });
		return reply(data);
	}
	return reply({ error: "not allowed" }, 405);
}

/** Microsoft's signing key [kid] (the list is kept for an hour). */
async function microsoftKey(kid) {
	if (!msKeys || Date.now() - msKeys.at > 3600_000 || !msKeys.keys.some((k) => k.kid === kid)) {
		const res = await fetch(MS_KEYS);
		msKeys = { at: Date.now(), keys: (await res.json()).keys || [] };
	}
	return msKeys.keys.find((k) => k.kid === kid) || null;
}

// ------------------------------------------------------------------ DNZ badge (who plays with DNZ Client)
// While a player is on a server, DNZ Client sends (every few minutes) the ids of the players it sees, signed
// in as above.

const ONLINE_TIME = 10 * 60; // seconds: a player counts as using DNZ Client this long after their last report
const ONLINE_MAX = 300; // ids per request
let onlineTable = false;

async function online(request, env) {
	const uuid = await session(request, env);
	if (!uuid) {
		return json({ error: "not signed in" }, 401);
	}
	if (!env.DB) {
		return json({ error: "no database" }, 503);
	}
	const body = await request.json();
	// the player's own name (sent by DNZ Client 1.40.0+), only for the owner's Players page
	const name = typeof body.name === "string" && /^[A-Za-z0-9_]{1,16}$/.test(body.name) ? body.name : null;
	const ids = (Array.isArray(body.players) ? body.players : []).filter((p) => /^[0-9a-f]{32}$/.test(p)).slice(0, ONLINE_MAX);
	const now = Math.floor(Date.now() / 1000);
	await onlineTableReady(env);
	const [, found] = await env.DB.batch([
		env.DB.prepare("INSERT INTO online (uuid, seen, name, first) VALUES (?1, ?2, ?3, ?2) ON CONFLICT(uuid) DO UPDATE SET seen = ?2, name = COALESCE(?3, name)")
			.bind(uuid, now, name),
		env.DB.prepare("SELECT o.uuid FROM json_each(?1) j JOIN online o ON o.uuid = j.value WHERE o.seen > ?2").bind(JSON.stringify(ids), now - ONLINE_TIME),
	]);
	return json({ dnz: found.results.map((r) => r.uuid) });
}

/** The "online" table with its newer columns (name, first) added to an older one. */
async function onlineTableReady(env) {
	if (onlineTable) {
		return;
	}
	await env.DB.prepare("CREATE TABLE IF NOT EXISTS online (uuid TEXT PRIMARY KEY, seen INTEGER NOT NULL, name TEXT, first INTEGER)").run();
	for (const column of ["name TEXT", "first INTEGER"]) {
		try {
			await env.DB.prepare(`ALTER TABLE online ADD COLUMN ${column}`).run();
		} catch (e) {
			// already there
		}
	}
	onlineTable = true;
}

// ------------------------------------------------------------------ Players page (owner only)
// cloud.dnzclient.com/admin lists the players of the "online" table (everyone who played on a server with DNZ
// Client and the DNZ badge on): name, first and last time seen.

async function accessEmail(request, env) {
	const token = request.headers.get("Cf-Access-Jwt-Assertion");
	if (!token || !env.ACCESS_TEAM || !env.ACCESS_AUD || !env.ADMIN_EMAIL) {
		return null;
	}
	const [head, payload, signature] = token.split(".");
	if (!head || !payload || !signature) {
		return null;
	}
	const header = JSON.parse(new TextDecoder().decode(fromB64url(head)));
	const certs = await (await fetch(`https://${env.ACCESS_TEAM}/cdn-cgi/access/certs`)).json();
	const jwk = (certs.keys || []).find((k) => k.kid === header.kid);
	if (!jwk || header.alg !== "RS256") {
		return null;
	}
	const key = await crypto.subtle.importKey("jwk", jwk, { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" }, false, ["verify"]);
	const ok = await crypto.subtle.verify("RSASSA-PKCS1-v1_5", key, fromB64url(signature), new TextEncoder().encode(`${head}.${payload}`));
	if (!ok) {
		return null;
	}
	const data = JSON.parse(new TextDecoder().decode(fromB64url(payload)));
	const audiences = Array.isArray(data.aud) ? data.aud : [data.aud];
	const email = String(data.email || "").toLowerCase();
	if (!audiences.includes(env.ACCESS_AUD) || data.iss !== `https://${env.ACCESS_TEAM}` || data.exp * 1000 < Date.now()
		|| email !== String(env.ADMIN_EMAIL).toLowerCase()) {
		return null;
	}
	return email;
}

async function adminPlayers(request, env) {
	if (!(await accessEmail(request, env))) {
		return new Response("Not allowed", { status: 403 });
	}
	if (!env.DB) {
		return new Response("No database (DB binding)", { status: 503 });
	}
	await onlineTableReady(env);
	const rows = (await env.DB.prepare("SELECT uuid, seen, name, first FROM online ORDER BY seen DESC LIMIT 2000").all()).results;
	const now = Math.floor(Date.now() / 1000);
	const day = rows.filter((r) => r.seen > now - 86400).length;
	const week = rows.filter((r) => r.seen > now - 7 * 86400).length;
	const nowOn = rows.filter((r) => r.seen > now - ONLINE_TIME).length;
	const esc = (t) => String(t).replace(/[&<>"']/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" })[c]);
	const ago = (t) => {
		const d = now - t;
		return d < 60 ? "az önce" : d < 3600 ? `${Math.floor(d / 60)} dk önce` : d < 86400 ? `${Math.floor(d / 3600)} saat önce` : `${Math.floor(d / 86400)} gün önce`;
	};
	const date = (t) => (t ? new Date(t * 1000).toLocaleString("tr-TR", { timeZone: "Europe/Istanbul" }) : "-");
	const list = rows.map((r) => {
		const id = esc(r.uuid);
		const online = r.seen > now - ONLINE_TIME;
		return `<tr><td><img src="https://mc-heads.net/avatar/${id}/28" width="28" height="28" alt=""></td>
<td><b>${r.name ? esc(r.name) : "<span class=m>(isim yok)</span>"}</b><div class=m>${id}</div></td>
<td>${online ? "<span class=on>● oyunda</span>" : esc(ago(r.seen))}<div class=m>${esc(date(r.seen))}</div></td>
<td class=m>${esc(date(r.first))}</td></tr>`;
	}).join("");
	const page = `<!doctype html><html lang="tr"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>DNZ Oyuncular</title><meta name="robots" content="noindex">
<style>body{margin:0;background:#0a0d12;color:#f2f4fa;font:14px system-ui,sans-serif;padding:24px}h1{margin:0 0 4px}
.m{color:#8c95ab;font-size:12px}.cards{display:flex;gap:12px;flex-wrap:wrap;margin:18px 0}.card{background:#151b25;border:1px solid #ffffff22;border-radius:12px;padding:14px 18px;min-width:120px}
.card b{display:block;font-size:24px}table{width:100%;border-collapse:collapse;background:#151b25;border-radius:12px;overflow:hidden}
td,th{padding:10px 12px;border-bottom:1px solid #ffffff12;text-align:left;vertical-align:middle}th{color:#8c95ab;font-weight:600;font-size:12px}
img{border-radius:6px;display:block}.on{color:#7ce38b;font-weight:600}</style></head><body>
<h1>DNZ Oyuncular</h1><div class=m>DNZ Client ile sunucuda oynayan ve DNZ Rozeti açık olan oyuncular. Sadece sen görebilirsin.</div>
<div class=cards><div class=card><b>${nowOn}</b>şu an oyunda</div><div class=card><b>${day}</b>son 24 saat</div><div class=card><b>${week}</b>son 7 gün</div><div class=card><b>${rows.length}</b>toplam</div></div>
<table><tr><th></th><th>Oyuncu</th><th>En son</th><th>İlk görülme</th></tr>${list || "<tr><td colspan=4 class=m>Henüz kimse yok.</td></tr>"}</table>
</body></html>`;
	return new Response(page, { headers: { "Content-Type": "text/html; charset=utf-8", "Cache-Control": "no-store", "X-Frame-Options": "DENY" } });
}

// ------------------------------------------------------------------ Launcher presence
// Every DNZ Launcher says about once a minute whether it is open ("launcher") or its game is running
// ("game"), with a random install id made on its first start (no account, name or IP is kept).

const PRESENCE_TIME = 150;
let presenceTable = false;

async function presenceReady(env) {
	if (presenceTable) {
		return;
	}
	await env.DB.prepare("CREATE TABLE IF NOT EXISTS presence (id TEXT PRIMARY KEY, state TEXT NOT NULL, seen INTEGER NOT NULL, first INTEGER NOT NULL)").run();
	await env.DB.prepare("CREATE INDEX IF NOT EXISTS presence_seen ON presence (seen)").run();
	presenceTable = true;
}

async function presenceCounts(env) {
	if (!env.DB) {
		return { launcher: 0, game: 0, total: 0 };
	}
	await presenceReady(env);
	const since = Math.floor(Date.now() / 1000) - PRESENCE_TIME;
	const rows = (await env.DB.prepare("SELECT state, COUNT(*) AS n FROM presence WHERE seen > ?1 AND state != 'off' GROUP BY state").bind(since).all()).results;
	const game = rows.filter((r) => r.state === "game").reduce((a, r) => a + r.n, 0);
	const launcher = rows.reduce((a, r) => a + r.n, 0);
	const total = (await env.DB.prepare("SELECT COUNT(*) AS n FROM presence").first()).n;
	// "launcher": everyone with DNZ Launcher open (also the ones playing), "game": the ones playing now,
	// "total": every installation that ever opened DNZ Launcher
	return { launcher, game, total };
}

async function presence(request, env) {
	if (!env.DB) {
		return json({ error: "no database" }, 503);
	}
	const body = await request.json();
	const id = String(body.id || "").toLowerCase();
	const state = ["launcher", "game", "off"].includes(body.state) ? body.state : "launcher";
	if (!/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/.test(id)) {
		return json({ error: "bad id" }, 400);
	}
	await presenceReady(env);
	const now = Math.floor(Date.now() / 1000);
	await env.DB.prepare("INSERT INTO presence (id, state, seen, first) VALUES (?1, ?2, ?3, ?3) ON CONFLICT(id) DO UPDATE SET state = ?2, seen = ?3")
		.bind(id, state, now).run();
	return json(await presenceCounts(env));
}
