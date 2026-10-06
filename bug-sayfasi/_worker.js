// DNZ bug reports: the form on index.html posts to /api/bug. Each report is sent to a Discord channel through a
// webhook, with its screenshots and log files attached. Every other path serves the static page.
// This is a Cloudflare Pages "advanced mode" _worker.js, so the folder can be uploaded by drag and drop.
//
// Settings (Cloudflare dashboard → Pages project → Settings → Variables and Secrets / Bindings):
//   DISCORD_WEBHOOK  secret text: the webhook URL of the Discord channel that receives reports (required)
//   BUG_KV           KV namespace (optional): limits each connection to a few reports per hour (stored as a hash)

const MAX_FILES = 3;
const MAX_SIZE = 8 * 1024 * 1024;
const OK_EXT = ["png", "jpg", "jpeg", "webp", "gif", "log", "txt"];
const REPORTS_PER_HOUR = 5;
const COLORS = { "Düşük": 0x3ddc97, "Orta": 0xffb74d, "Yüksek": 0xff9f43, "Çökme": 0xff5e62 };

export default {
	async fetch(request, env) {
		const url = new URL(request.url);
		if (url.pathname === "/api/bug") {
			if (request.method !== "POST") {
				return json({ error: "method not allowed" }, 405);
			}
			try {
				return await report(request, env);
			} catch {
				return json({ error: "eServer" }, 500);
			}
		}
		return env.ASSETS.fetch(request);
	},
};

async function report(request, env) {
	if (!env.DISCORD_WEBHOOK) {
		return json({ error: "eServer" }, 500);
	}
	let form;
	try {
		form = await request.formData();
	} catch {
		return json({ error: "eServer" }, 400);
	}
	// Bots fill the hidden "website" field; pretend it worked.
	if (field(form, "website", 200)) {
		return json({ ok: true, id: newId() });
	}
	const title = field(form, "title", 120);
	const description = field(form, "description", 3000);
	if (!title || !description) {
		return json({ error: "eRequired" }, 400);
	}
	const files = form.getAll("files").filter(f => typeof f === "object" && f.size > 0);
	if (files.length > MAX_FILES) {
		return json({ error: "eFiles" }, 400);
	}
	for (const f of files) {
		if (f.size > MAX_SIZE || !OK_EXT.includes(f.name.split(".").pop().toLowerCase())) {
			return json({ error: "eServer" }, 400);
		}
	}
	if (!(await allowed(request, env))) {
		return json({ error: "eRate" }, 429);
	}

	const id = newId();
	const severity = field(form, "severity", 20) || "Orta";
	const steps = field(form, "steps", 2000);
	const name = field(form, "name", 40);
	const contact = field(form, "contact", 80);
	const fields = [
		{ name: "Ürün", value: field(form, "product", 20) || "-", inline: true },
		{ name: "Ciddiyet", value: severity, inline: true },
		{ name: "DNZ sürümü", value: field(form, "version", 30) || "-", inline: true },
		{ name: "Minecraft", value: field(form, "mc", 10) || "-", inline: true },
		{ name: "Sistem", value: field(form, "os", 20) || "-", inline: true },
		{ name: "Dil", value: field(form, "lang", 5) || "-", inline: true },
	];
	if (steps) fields.push({ name: "Nasıl tekrar olur", value: cut(steps, 1024) });
	if (name || contact) fields.push({ name: "İletişim", value: cut([name, contact].filter(Boolean).join(" · "), 1024) });

	const payload = {
		username: "DNZ Hata Bildirimi",
		allowed_mentions: { parse: [] },
		embeds: [{
			title: cut(`#${id} · ${title}`, 256),
			description: cut(description, 4000),
			color: COLORS[severity] ?? 0x4fa3ff,
			fields,
			timestamp: new Date().toISOString(),
			footer: { text: "bug-sayfasi" },
		}],
	};
	const body = new FormData();
	body.append("payload_json", JSON.stringify(payload));
	files.forEach((f, i) => body.append(`files[${i}]`, f, safeName(f.name)));

	const res = await fetch(env.DISCORD_WEBHOOK, { method: "POST", body });
	if (!res.ok) {
		return json({ error: "eServer" }, 502);
	}
	return json({ ok: true, id });
}

function field(form, key, max) {
	return String(form.get(key) ?? "").trim().slice(0, max);
}

function cut(text, max) {
	return text.length > max ? text.slice(0, max - 1) + "…" : text;
}

function safeName(name) {
	return name.replace(/[^\w.\-]+/g, "_").slice(-80);
}

function newId() {
	return Date.now().toString(36).slice(-4).toUpperCase() + Math.random().toString(36).slice(2, 4).toUpperCase();
}

async function allowed(request, env) {
	if (!env.BUG_KV) {
		return true;
	}
	const ip = request.headers.get("CF-Connecting-IP") || "unknown";
	const hash = await crypto.subtle.digest("SHA-256", new TextEncoder().encode("dnz-bug:" + ip));
	const key = "rate:" + [...new Uint8Array(hash)].slice(0, 12).map(b => b.toString(16).padStart(2, "0")).join("");
	const count = Number(await env.BUG_KV.get(key)) || 0;
	if (count >= REPORTS_PER_HOUR) {
		return false;
	}
	await env.BUG_KV.put(key, String(count + 1), { expirationTtl: 3600 });
	return true;
}

function json(data, status = 200) {
	return new Response(JSON.stringify(data), { status, headers: { "Content-Type": "application/json; charset=utf-8" } });
}
