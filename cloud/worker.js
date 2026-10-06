// DNZ Cloud: keeps a player's DNZ Client settings (key bindings, DNZ menu and HUD settings) so they follow the
// player to another computer. Runs as a Cloudflare Worker at cloud.dnzclient.com.
//
// Sign-in without passwords or tokens: Minecraft gives every player a key pair whose public key Mojang signs
// (the "player certificate" the game uses to sign chat). The game signs a fresh challenge from this worker with
// that key; the worker checks Mojang's signature on the key (Mojang's public keys are below, from
// api.minecraftservices.com/publickeys) and the signature on the challenge. Mojang is never asked (it blocks
// requests from Cloudflare), and the player's access token never reaches DNZ.
//
// Bindings (Cloudflare dashboard → Worker → Settings):
//   BUCKET  R2 bucket for the settings files (private, e.g. "dnz-cloud")
//   SECRET  secret text, a long random value (signs the challenges and the short-lived session tokens)

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
			return json({ error: "bad request" }, 400);
		}
	},
};

/**
 * {uuid, expiresAt, publicKey, keySignature, challenge, signature} → a session token for one hour, when Mojang
 * signed the player's key for that uuid and that key signed the challenge.
 */
async function auth(request, env) {
	const b = await request.json();
	if (!/^[0-9a-f]{32}$/.test(b.uuid) || typeof b.expiresAt !== "number" || b.expiresAt < Date.now()) {
		return json({ error: "bad request" }, 400);
	}
	// The challenge must come from this worker and be fresh.
	const [nonce, mac] = String(b.challenge).split(".");
	if (!nonce || mac !== (await sign(nonce, env.SECRET))
		|| JSON.parse(new TextDecoder().decode(fromB64url(nonce))).t < Date.now() / 1000 - CHALLENGE_TIME) {
		return json({ error: "bad challenge" }, 401);
	}
	const publicKey = fromB64(b.publicKey);
	// What Mojang signed: uuid (16 bytes) + expiry in milliseconds (8 bytes) + the player's public key.
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
