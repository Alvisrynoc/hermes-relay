from __future__ import annotations

import importlib.util
import json
import sys
import tempfile
import unittest
from pathlib import Path


SCRIPT = Path(__file__).parents[1] / "check-gateway-scenario-conformance.py"
SPEC = importlib.util.spec_from_file_location("gateway_scenario_conformance", SCRIPT)
assert SPEC and SPEC.loader
module = importlib.util.module_from_spec(SPEC)
sys.modules[SPEC.name] = module
SPEC.loader.exec_module(module)


SERVER_SOURCE = '''
def _clarify_block(sid, q, c, multi_select=False, questions=None):
    server_requests.send("clarify", sid, {}, timeout=60)
    return {"questions": [{"qid": "q0", "question": q, "choices": c, "multi_select": multi_select}]}

def _respond(rid, params, key):
    return {"question_id": params.get("question_id"), "request_id": params.get("request_id"),
            "answers": {}, "remaining": [], "status": "expired"}

def _pending_clarify_request_payload(sid):
    return {"answers": {}, "event": "clarify.request"}

def _block(event, sid, payload):
    return {"answers": {}, "timed_out": True}

def _session_info(agent, session=None):
    return {"running": bool((session or {}).get("running"))}

def _emit_settled_session_info(sid, session, agent):
    _emit("session.info", sid, _session_info(agent, session))

def _live_session_payload(sid, session):
    inflight = _inflight_snapshot(session)
    return {
        "session_id": sid, "session_key": "key", "messages": [],
        "running": False, "status": "idle",
    }

def _start_agent_build(sid, session):
    _emit("session.info", sid, {"lazy": False})
    _emit("error", sid, {"message": "agent init failed: fixture"})
    ready.set()

def _run_prompt_submit(sid, session, agent):
    if _ensure_active_session_slot(sid, session) is not None:
        _emit("error", sid, {})
        return False
    try:
        _emit("message.complete", sid, {})
    finally:
        session["running"] = False
        _emit_settled_session_info(sid, session, agent)

def _session_pending_kind(sid):
    return "approval" if sid in _pending else ""

def _session_live_status(sid, session):
    if _session_pending_kind(sid):
        return "waiting"
    ready = session.get("agent_ready")
    if ready is not None and not ready.is_set() and session.get("agent_build_started"):
        return "starting"
    if session.get("running"):
        return "working"
    return "idle"

def _session_live_item(sid, session, current_sid=""):
    return {
        "id": sid,
        "session_key": session.get("session_key", sid),
        "status": _session_live_status(sid, session),
    }

def _mirror_subagent_child(event):
    child = event.get("child_session_id")
    if event.get("type") == "subagent.text":
        return (child, "reasoning.delta", "message.delta")
    return child
'''

METHODS_SOURCE = '''
def method(name):
    return lambda fn: fn

@method("session.create")
def _(rid, params):
    _schedule_agent_build("live")
    return {"session_id": "live", "info": {"lazy": True}}

@method("session.resume")
def _(rid, params):
    target = params.get("session_id", "")
    lazy = bool(params.get("lazy"))
    close_on_disconnect = bool(params.get("close_on_disconnect"))
    include_ancestors = not lazy
    found = db.get_session(target)
    if not found:
        return _err(rid, 4007, "session not found")
    try:
        db.assert_resume_safe(target)
    except TooLarge:
        return _err(rid, 4130, "too large")
    target = db.resolve_resume_session_id(target)
    live = _find_live_session_by_key(target)
    if live:
        payload = _live_session_payload(*live)
        payload["resumed"] = target
        return payload
    return {
        "session_id": "live", "session_key": target, "messages": [],
        "running": False, "status": "idle", "resumed": target,
    }

@method("session.activate")
def _(rid, params):
    session, error = _sess_nowait(params, rid)
    return _live_session_payload(params["session_id"], session)

def _snapshot_sessions(rid):
    with _sessions_lock:
        return list(_sessions.items()), None

@method("session.events.since")
def _(rid, params):
    return {"open_requests": _open_requests(params["session_id"])}

@method("session.active_list")
def _(rid, params):
    snapshot, error = _snapshot_sessions(rid)
    if error:
        return error
    current = str(params.get("current_session_id") or "")
    rows = [_session_live_item(sid, session, current) for sid, session in snapshot]
    return _ok(rid, {"sessions": rows})
'''

PROMPT_METHODS_SOURCE = '''
def method(name):
    return lambda fn: fn

@method("prompt.submit")
def _(rid, params):
    session = sessions[params["session_id"]]
    if (refusal := _ensure_active_session_slot(params["session_id"], session)) is not None:
        return _err(rid, 4090, str(refusal), {"reason": refusal.reason})
    return _ok(rid, {"ok": True})
'''

ACTIVE_SESSIONS_SOURCE = '''
SESSION_NOT_OWNED = "SESSION_NOT_OWNED"
PER_SESSION_EXCLUSIVE_SUBMIT = True

def session_already_owned_message(session_id, entry):
    return f"Session {session_id} already has a live owner ({entry}). Only one surface at a time may run a session."
'''

API_SOURCE = '''
ROUTES = [
    ("POST", "/api/sessions/{session_id}/chat/stream"),
    ("POST", "/v1/chat/completions"),
    ("POST", "/v1/runs"),
    ("GET", "/v1/runs/{run_id}/events"),
]

async def _handle_session_chat_stream(request):
    return ("assistant.completed", "run.completed", "done")

async def _handle_chat_completions(request):
    return "openai"

async def _handle_runs(request):
    return ("message.delta", "run.completed")
'''


NATIVE_METHODS = ("clarify", "approval", "sudo", "secret", "vault.unlock_prompt", "vault.save_login",
                  "vault.code", "terminal.read", "preview.read", "preview.act", "window.read", "tour")
NATIVE_REQUEST_SOURCE = """
def frame(self):
    return {"jsonrpc":"2.0", "id":self.id, "method":self.method, "params":{"session_id":self.sid}}
def snapshot(self):
    return {"answers": self.locked}
def resolve_response(frame):
    return frame.get("error") or frame.get("result", {}).get("answers")
def lock_answer(request_id, question_id, answer):
    req.locked[question_id] = answer
def send(method, sid, params):
    _unanswerable(method, sid)
    return {"answers": {}, "timed_out": True, "reason": "timeout"}
def _emit_cancel(req, reason):
    _emit("request.cancel", req.sid, {"id":req.id, "method":req.method, "reason":reason})
"""


class GatewayScenarioConformanceTest(unittest.TestCase):
    def _write_authority_fixture(self):
        sources = {
            "tui_gateway/ws.py": '''
def _authority_connection(ws, transport, identity, operator):
    authority = ws.app.state.session_authority
    return AuthorityConnection(authority, transport, identity, operator=operator)
async def _dispatch_request(connection, req, method, transport):
    response = await connection.dispatch(req)
    if legacy_fallback_allowed(connection.actor, method):
        return dispatch_legacy(server, req, transport, connection.actor)
    return response
async def handle_ws(ws):
    return {"session_authority": True}
''',
            "tui_gateway/ws_legacy_fallback.py": '''
_SESSION_NAMESPACES = ("session.", "prompt.", "message.")
def legacy_fallback_allowed(actor, method):
    if method.startswith(_SESSION_NAMESPACES) and method not in _SESSION_FALLBACK_ALLOWED:
        return False
    return actor.capabilities
''',
            "tui_gateway/contracts/canonical_sessions.py": '''
class CanonicalControlParams(CanonicalSessionParams):
    execution_generation: int
class CanonicalApprovalParams(CanonicalControlParams):
    prompt_id: str
    choice: str
class CanonicalResumeParams(ProfileParams):
    session_id: str
    omit_messages: bool = False
class CanonicalSnapshot(Result):
    session_id: str
    execution_generation: int
    authority_epoch: int
    subscription_id: str
    replay_epoch: str
    last_sequence: int
    pending: list
    prompts: list
for name, params, result in (
    ("session.resume", CanonicalResumeParams, CanonicalSnapshot),
    ("session.interrupt", CanonicalControlParams, CanonicalSessionHandle),
    ("approval.respond", CanonicalApprovalParams, PromptResponseResult),
):
    canonical_method(name, params=params, result=result)
''',
            "gateway/session_controls.py": '''
class AuthorityConnection:
    async def dispatch(self, request):
        contract = CANONICAL_METHODS.get(request["method"])
        if canonical_param_problems(contract, request["params"]):
            return {"error": {"code": 4001, "message": "invalid_params"}}
        return self.handlers()[request["method"]]()
    async def respond(self, ref, params):
        if set(params) != {"session_id", "execution_generation", "prompt_id", "choice"}:
            raise RuntimeStoreError("invalid_params")
        return controls.respond(ref.session_id, params["execution_generation"], params["prompt_id"], params["choice"])
''',
        }
        for relative, source in sources.items():
            path = self.root / relative
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text(source, encoding="utf-8")

    def test_authority_contract_requires_generation_prompt_and_snapshot_identity(self):
        self._write_authority_fixture()
        self.assertTrue(module.audit_sources(self.root, (module.SESSION_AUTHORITY,))[0].passed)
        for relative, before, after in (
            ("tui_gateway/contracts/canonical_sessions.py", "execution_generation: int", "other_generation: int"),
            ("tui_gateway/contracts/canonical_sessions.py", "prompts: list", "other_prompts: list"),
            ("gateway/session_controls.py", "canonical_param_problems(contract", "ignore_params(contract"),
            ("gateway/session_controls.py", '"prompt_id", "choice"', '"other_id", "choice"'),
            ("tui_gateway/ws.py", "legacy_fallback_allowed(connection.actor, method)", "True"),
        ):
            path = self.root / relative
            original = path.read_text(encoding="utf-8")
            with self.subTest(removed=before):
                path.write_text(original.replace(before, after), encoding="utf-8")
                self.assertFalse(module.audit_sources(self.root, (module.SESSION_AUTHORITY,))[0].passed)
                path.write_text(original, encoding="utf-8")

    def test_default_audit_includes_authority_when_gateway_has_that_surface(self):
        self._write_authority_fixture()
        results = module.audit_sources(self.root, module.ALL_CONTRACTS)
        self.assertEqual(module.SESSION_AUTHORITY, results[-1].contract)
        self.assertTrue(all(result.passed for result in results))

    def test_split_ticket_parser_is_followed_without_accepting_unreachable_copy(self):
        auth = self.root / "hermes_cli/web_server_chat.py"
        boundary = self.root / "hermes_cli/web_server_boundary.py"
        original = auth.read_text(encoding="utf-8")
        admission_start = original.index("def _ws_auth_reason")
        boundary.write_text(original[:admission_start], encoding="utf-8")
        auth.write_text("from hermes_cli.web_server_boundary import (\n"
                        "    _gateway_ws_ticket_from_subprotocol, _GATEWAY_WS_PROTOCOL,\n"
                        ")\n" + original[admission_start:], encoding="utf-8")
        self.assertTrue(module.audit_sources(self.root, (module.TICKET_PROTOCOL,))[0].passed)
        boundary.write_text(original[:admission_start].replace('"invalid"', '"accepted"'), encoding="utf-8")
        self.assertFalse(module.audit_sources(self.root, (module.TICKET_PROTOCOL,))[0].passed)

    def test_split_initialization_uses_current_registry_and_announcement(self):
        registry = self.root / "tui_gateway/session_registry.py"
        registry.write_text(SERVER_SOURCE, encoding="utf-8")
        self.assertTrue(module.audit_sources(self.root, (module.SESSION_INITIALIZATION,))[0].passed)
        registry.write_text(SERVER_SOURCE.replace('"agent init failed: fixture"', '"unrelated"'), encoding="utf-8")
        self.assertFalse(module.audit_sources(self.root, (module.SESSION_INITIALIZATION,))[0].passed)

    def test_initialization_error_uses_verified_shared_message_helper(self):
        server = self.root / module.SERVER
        server.write_text(SERVER_SOURCE + '\nfrom tui_gateway.user_messages import agent_init_failed_message\n', encoding="utf-8")
        helper = self.root / "tui_gateway/user_messages.py"
        helper.write_text('def agent_init_failed_message(exc):\n    return f"Cannot start: {exc}"\n', encoding="utf-8")
        registry = self.root / "tui_gateway/session_registry.py"
        registry.write_text(SERVER_SOURCE.replace('"agent init failed: fixture"', 'agent_init_failed_message(error)'), encoding="utf-8")
        self.assertTrue(module.audit_sources(self.root, (module.SESSION_INITIALIZATION,))[0].passed)
        helper.write_text('def unrelated(exc):\n    return str(exc)\n', encoding="utf-8")
        self.assertFalse(module.audit_sources(self.root, (module.SESSION_INITIALIZATION,))[0].passed)

    def test_sse_dynamic_terminal_status_must_preserve_completed_failed_cancelled(self):
        api = self.root / module.API_SERVER
        updated = API_SOURCE.replace('return ("assistant.completed", "run.completed", "done")',
            'status, fields = runs.terminal_run_status(result)\n'
            '    return ("assistant.completed", f"run.{status}", "done")')
        updated = 'from gateway.platforms import api_server_runs as runs\n' + updated
        api.write_text(updated, encoding="utf-8")
        helper = self.root / "gateway/platforms/api_server_runs.py"
        helper.write_text('def terminal_run_status(result):\n'
            '    return "cancelled" if result.get("interrupted") else "failed" if result.get("failed") else "completed", {}\n', encoding="utf-8")
        self.assertTrue(module.audit_sources(self.root, (module.API_BOUNDARY,))[0].passed)
        helper.write_text(helper.read_text().replace('"completed"', '"unknown"'), encoding="utf-8")
        self.assertFalse(module.audit_sources(self.root, (module.API_BOUNDARY,))[0].passed)

    def test_batch_settlement_requires_locked_answers_and_timeout_outcome(self):
        source = self.root / "tui_gateway/server_requests.py"
        updated = NATIVE_REQUEST_SOURCE.replace(
            'return frame.get("error") or frame.get("result", {}).get("answers")',
            'result = frame.get("error") or frame.get("result", {})\n    return settle_batch(req.locked, result)')
        updated = updated.replace('return {"answers": {}, "timed_out": True, "reason": "timeout"}',
                                  'return settle_batch(locked, ended="timed_out")')
        source.write_text(updated, encoding="utf-8")
        helper = self.root / "tools/clarify_batch.py"
        helper.parent.mkdir(parents=True, exist_ok=True)
        helper.write_text('def settle_batch(locked, result=None, *, ended=None):\n'
                          '    return {"answers": dict(locked), "outcome": ended}\n', encoding="utf-8")
        self.assertTrue(module.audit_sources(self.root, (module.SERVER_REQUESTS,))[0].passed)
        source.write_text(updated.replace('settle_batch(req.locked, result)', 'settle_batch({}, result)'), encoding="utf-8")
        self.assertFalse(module.audit_sources(self.root, (module.SERVER_REQUESTS,))[0].passed)

    def test_split_api_routes_require_registered_completion_handler(self):
        api = self.root / module.API_SERVER
        routes = self.root / "gateway/platforms/api_server_openai_routes.py"
        routes.write_text('async def _handle_chat_completions(request):\n    return "openai"\n', encoding="utf-8")
        api.write_text(API_SOURCE.replace('async def _handle_chat_completions(request):\n    return "openai"\n',
            'from gateway.platforms.api_server_openai_routes import _handle_chat_completions\n'), encoding="utf-8")
        self.assertTrue(module.audit_sources(self.root, (module.API_BOUNDARY,))[0].passed)
        routes.write_text('async def unrelated(request):\n    return "openai"\n', encoding="utf-8")
        self.assertFalse(module.audit_sources(self.root, (module.API_BOUNDARY,))[0].passed)

    def test_clarify_conformance_requires_question_ownership_and_replay(self) -> None:
        results = module.audit_sources(self.root, [module.CLARIFY])
        self.assertTrue(results[0].passed)
        source = self.root / module.SERVER
        source.write_text(SERVER_SOURCE.replace('"remaining"', '"other"'), encoding="utf-8")
        self.assertFalse(module.audit_sources(self.root, [module.CLARIFY])[0].passed)

    def test_native_contract_rejects_missing_capability_gate_and_methods(self):
        self.assertTrue(module.audit_sources(self.root, [module.SERVER_REQUESTS])[0].passed)
        source = self.root / "tui_gateway/server_requests.py"
        source.write_text(NATIVE_REQUEST_SOURCE.replace("_unanswerable(method, sid)", "pass"), encoding="utf-8")
        self.assertFalse(module.audit_sources(self.root, [module.SERVER_REQUESTS])[0].passed)
        source.write_text(NATIVE_REQUEST_SOURCE, encoding="utf-8")
        contract = self.root / "tui_gateway/contracts/server_requests.py"
        contract.write_text(contract.read_text().replace("'clarify'", "'clarify.future'"), encoding="utf-8")
        self.assertFalse(module.audit_sources(self.root, [module.SERVER_REQUESTS])[0].passed)

    def test_native_contract_allows_additive_request_methods(self):
        contract = self.root / "tui_gateway/contracts/server_requests.py"
        contract.write_text(contract.read_text() + '\nserver_request("setup_choose", params=Params, result=Result)\n', encoding="utf-8")
        self.assertTrue(module.audit_sources(self.root, [module.SERVER_REQUESTS])[0].passed)

    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.root = Path(self.temp.name)
        sources = {
            module.SERVER: SERVER_SOURCE,
            module.SESSION_METHODS: METHODS_SOURCE,
            module.PROMPT_METHODS: PROMPT_METHODS_SOURCE,
            module.ACTIVE_SESSIONS: ACTIVE_SESSIONS_SOURCE,
            module.API_SERVER: API_SOURCE,
            "hermes_cli/web_server_chat.py": '''
_GATEWAY_WS_PROTOCOL = "hermes-gateway-v1"
_GATEWAY_WS_TICKET_PROTOCOL_PREFIX = "hermes-gateway-ticket."
def _gateway_ws_ticket_from_subprotocol(ws):
    return ws.headers.get("sec-websocket-protocol"), "ok", "invalid"
def _ws_auth_reason(ws):
    ticket = _gateway_ws_ticket_from_subprotocol(ws) or ws.query_params.get("ticket")
    consume_ticket(ticket)
    ws._hermes_ws_subprotocol = _GATEWAY_WS_PROTOCOL
    return None, "ticket-subprotocol"
''',
            "hermes_cli/web_routers/chat_ws.py": '''
async def gateway_ws(ws):
    await handle_ws(ws, subprotocol=getattr(ws, "_hermes_ws_subprotocol", None))
''',
        }
        sources["tui_gateway/methods_subagents.py"] = '\n'.join([
            '_SUBAGENT_SNAPSHOT_FIELDS = ("subagent_id",)',
            '@method("subagent.list")', 'def _(rid, params):',
            '    owner = _current_session_steer_authority(params["session_id"])',
            '    return {"subagents": _visible_subagent_records(owner), "delegations": []}',
        ])
        sources.update({
            "tui_gateway/server_requests.py": NATIVE_REQUEST_SOURCE,
            "tui_gateway/contracts/server_requests.py": "\n".join(
                f'server_request({name!r}, params=Params, result=Result)' for name in NATIVE_METHODS),
            "tui_gateway/methods_voice.py": '@method("client.capabilities")\ndef _(rid, params):\n    advertise(transport, params["server_requests"])\n',
        })
        sources[module.PROMPT_METHODS] += '\n@method("clarify.lock")\ndef _(rid, params):\n    return {"status": "expired", "remaining": lock_answer(params["request_id"], params["question_id"], params["answer"])}\n'
        for relative, text in sources.items():
            path = self.root / relative
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text(text, encoding="utf-8")

    def tearDown(self):
        self.temp.cleanup()

    def test_subagent_roster_requires_authority_and_stable_identity(self):
        self.assertTrue(module.audit_sources(self.root, (module.SUBAGENT_ROSTER,))[0].passed)
        path = self.root / "tui_gateway/methods_subagents.py"
        path.write_text(path.read_text().replace("_current_session_steer_authority", "unscoped_owner"), encoding="utf-8")
        self.assertFalse(module.audit_sources(self.root, (module.SUBAGENT_ROSTER,))[0].passed)
        path.write_text("def unrelated(): pass\n", encoding="utf-8")
        self.assertFalse(module.audit_sources(self.root, (module.SUBAGENT_ROSTER,))[0].passed)
        path.unlink()
        self.assertFalse(module.audit_sources(self.root, (module.SUBAGENT_ROSTER,))[0].passed)

    def test_complete_contract_fixture_passes(self):
        results = module.audit_sources(self.root, module.ALL_CONTRACTS)

        self.assertEqual(module.ALL_CONTRACTS, tuple(result.contract for result in results))
        self.assertTrue(all(result.passed for result in results), results)

    def test_resume_follows_extracted_helpers_but_not_unreachable_history(self):
        path = self.root / module.SESSION_METHODS
        original = path.read_text(encoding="utf-8")
        marker = '@method("session.resume")\ndef _(rid, params):'
        extracted = original.replace(marker,
            '@method("session.resume")\ndef _(rid, params):\n'
            '    return _resume_impl(rid, params)\n\ndef _resume_impl(rid, params):')
        path.write_text(extracted, encoding="utf-8")
        self.assertTrue(module.audit_sources(self.root, (module.SESSION_RESUME,))[0].passed)
        path.write_text(extracted.replace('return _resume_impl(rid, params)', 'return {}'), encoding="utf-8")
        self.assertFalse(module.audit_sources(self.root, (module.SESSION_RESUME,))[0].passed)

    def test_extracted_registry_keeps_identity_and_status_requirements(self):
        path = self.root / "tui_gateway/session_registry.py"
        path.write_text(SERVER_SOURCE, encoding="utf-8")
        requirements = (module.SESSION_ACTIVATE, module.SESSION_ACTIVE_LIST)
        self.assertTrue(all(result.passed for result in module.audit_sources(self.root, requirements)))
        # Intact legacy helpers must not mask a broken extracted implementation.
        path.write_text(SERVER_SOURCE.replace('"session_key"', '"other_key"'), encoding="utf-8")
        self.assertTrue(all(not result.passed for result in module.audit_sources(self.root, requirements)))
        path.write_text(SERVER_SOURCE.replace('"waiting"', '"other_state"'), encoding="utf-8")
        self.assertFalse(module.audit_sources(self.root, (module.SESSION_ACTIVE_LIST,))[0].passed)

    def test_ticket_protocol_requires_public_selection_and_single_use_admission(self):
        for relative, before, after in (
            ("hermes_cli/web_server_chat.py", "consume_ticket(ticket)", "accept(ticket)"),
            ("hermes_cli/web_server_chat.py", "ws._hermes_ws_subprotocol = _GATEWAY_WS_PROTOCOL",
             "ws._hermes_ws_subprotocol = ticket"),
            ("hermes_cli/web_routers/chat_ws.py", "subprotocol=getattr", "ignored=getattr"),
        ):
            path = self.root / relative
            original = path.read_text(encoding="utf-8")
            with self.subTest(relative=relative, removed=before):
                path.write_text(original.replace(before, after), encoding="utf-8")
                self.assertFalse(module.audit_sources(self.root, (module.TICKET_PROTOCOL,))[0].passed)
                path.write_text(original, encoding="utf-8")

    def test_activate_accepts_verified_upstream_session_decorator(self):
        path = self.root / module.SESSION_METHODS
        wrapped = METHODS_SOURCE.replace('@method("session.activate")', '@_session_method("session.activate")')
        wrapped = wrapped.replace('    session, error = _sess_nowait(params, rid)\n', '')
        wrapped += '''
_with_session = _session_arg(lambda params, rid: _sess_nowait(params, rid))
def _session_method(name: str, *, live: bool = False):
    return lambda fn: method(name)((_with_live_session if live else _with_session)(fn))
'''
        path.write_text(wrapped, encoding="utf-8")
        self.assertTrue(module.audit_sources(self.root, (module.SESSION_ACTIVATE,))[0].passed)
        path.write_text(wrapped.replace('_sess_nowait(params, rid)', '_sess(params, rid)'), encoding="utf-8")
        self.assertFalse(module.audit_sources(self.root, (module.SESSION_ACTIVATE,))[0].passed)

    def test_missing_terminal_emit_fails_only_terminal_contract(self):
        path = self.root / module.SERVER
        path.write_text(
            SERVER_SOURCE.replace('"message.complete"', '"message.delta"'),
            encoding="utf-8",
        )

        results = module.audit_sources(
            self.root, (module.GATEWAY_TERMINAL, module.GATEWAY_SETTLED_INFO)
        )

        self.assertFalse(results[0].passed)
        self.assertIn("message.complete", results[0].problem)
        self.assertTrue(results[1].passed)

    def test_decomposed_child_watch_and_terminal_contracts(self):
        prompt = self.root / "tui_gateway/prompt_turn.py"
        prompt.write_text('def _run_prompt_submit():\n    _emit("message.complete", sid, {})\n', encoding="utf-8")
        methods = self.root / module.SESSION_METHODS
        source = '''
class _Resume:
    def __init__(self, params):
        self.lazy = params.get("lazy")
        self.close = params.get("close_on_disconnect")
    def child_history(self):
        return self.db.get_messages_as_conversation(self.target, include_row_ids=True)
def _resume_lazy(ctx):
    history = ctx.child_history()
    return ctx.record(history, lazy=True)
@method("session.resume")
def resume(rid, params):
    ctx = _Resume(params)
    if ctx.lazy:
        return _resume_lazy(ctx)
'''
        methods.write_text(source, encoding="utf-8")
        mirror = self.root / "tui_gateway/agent_callbacks.py"
        mirror.write_text('def _mirror_subagent_to_child():\n    return ("child_session_id", "subagent.text", "reasoning.delta", "message.delta")\n', encoding="utf-8")
        requirements = (module.GATEWAY_TERMINAL, module.SUBAGENT_CHILD_WATCH)
        self.assertTrue(all(result.passed for result in module.audit_sources(self.root, requirements)))
        methods.write_text(source.replace('include_row_ids=True', 'include_ancestors=True'), encoding="utf-8")
        self.assertFalse(module.audit_sources(self.root, requirements)[1].passed)
        methods.write_text(source.replace('"lazy"', '"not_lazy"'), encoding="utf-8")
        self.assertFalse(module.audit_sources(self.root, requirements)[1].passed)

    def test_settlement_must_clear_running_before_info(self):
        path = self.root / module.SERVER
        reordered = SERVER_SOURCE.replace(
            '        session["running"] = False\n'
            '        _emit_settled_session_info(sid, session, agent)',
            '        _emit_settled_session_info(sid, session, agent)\n'
            '        session["running"] = False',
        )
        path.write_text(reordered, encoding="utf-8")

        result = module.audit_sources(self.root, (module.GATEWAY_SETTLED_INFO,))[0]

        self.assertFalse(result.passed)
        self.assertIn("before settled", result.problem)

    def test_decomposed_settlement_retains_ordering_check(self):
        helper = self.root / "tui_gateway/session_workdir.py"
        helper.write_text('def _emit_settled_session_info(sid, session, agent):\n    _emit("session.info", sid, _session_info(agent, session))\n', encoding="utf-8")
        prompt = self.root / "tui_gateway/prompt_turn.py"
        source = 'def _run_prompt_submit():\n    try:\n        pass\n    finally:\n        session["running"] = False\n        _emit_settled_session_info(sid, session, agent)\n'
        prompt.write_text(source, encoding="utf-8")
        self.assertTrue(module.audit_sources(self.root, (module.GATEWAY_SETTLED_INFO,))[0].passed)
        prompt.write_text(source.replace('session["running"] = False', 'session["running"] = True'), encoding="utf-8")
        self.assertFalse(module.audit_sources(self.root, (module.GATEWAY_SETTLED_INFO,))[0].passed)

    def test_manifest_selects_known_contract_subset(self):
        manifest = self.root / "scenario.json"
        manifest.write_text(
            json.dumps(
                {
                    "contract_requirements": [
                        module.SESSION_ACTIVATE,
                        module.GATEWAY_SETTLED_INFO,
                    ]
                }
            ),
            encoding="utf-8",
        )

        requirements = module.load_requirements(manifest)

        self.assertEqual((module.GATEWAY_SETTLED_INFO, module.SESSION_ACTIVATE), requirements)

    def test_active_list_requires_waiting_to_outrank_working(self):
        path = self.root / module.SERVER
        reordered = SERVER_SOURCE.replace(
            '    if _session_pending_kind(sid):\n'
            '        return "waiting"\n'
            '    ready = session.get("agent_ready")',
            '    if session.get("running"):\n'
            '        return "working"\n'
            '    if _session_pending_kind(sid):\n'
            '        return "waiting"\n'
            '    ready = session.get("agent_ready")',
        )
        path.write_text(reordered, encoding="utf-8")

        result = module.audit_sources(self.root, (module.SESSION_ACTIVE_LIST,))[0]

        self.assertFalse(result.passed)
        self.assertIn("precedence", result.problem)

    def test_manifest_rejects_unknown_contract(self):
        manifest = self.root / "scenario.json"
        manifest.write_text(json.dumps({"requires": ["relay.private_route"]}), encoding="utf-8")

        with self.assertRaisesRegex(ValueError, "unknown scenario contract"):
            module.load_requirements(manifest)

    def test_fork_markers_are_rejected(self):
        path = self.root / module.API_SERVER
        path.write_text(API_SOURCE + '\nFORK = "RelayPlugin"\n', encoding="utf-8")

        with self.assertRaisesRegex(ValueError, "fork marker"):
            module.audit_sources(self.root, module.ALL_CONTRACTS)


if __name__ == "__main__":
    unittest.main()
