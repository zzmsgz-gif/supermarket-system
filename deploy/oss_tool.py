import os
import sys
import time
import hashlib
import hmac
import base64
import urllib.request
import urllib.error

endpoint = os.environ["OSS_ENDPOINT"]      # https://oss-cn-beijing.aliyuncs.com
bucket = os.environ["OSS_BUCKET"]          # super-zzm
ak = os.environ["OSS_ACCESS_KEY_ID"]
sk = os.environ["OSS_ACCESS_KEY_SECRET"]


def sign(verb, resource, headers, content_md5="", content_type=""):
    date = time.strftime("%a, %d %b %Y %H:%M:%S GMT", time.gmtime())
    oss_headers = []
    for k in sorted(headers.keys()):
        if k.lower().startswith("x-oss-"):
            oss_headers.append(k.lower() + ":" + headers[k].strip())
    canon_oss = "".join(h + "\n" for h in oss_headers)
    string_to_sign = (
        verb + "\n" + content_md5 + "\n" + content_type + "\n" + date + "\n"
        + canon_oss + resource
    )
    sig = base64.b64encode(
        hmac.new(sk.encode(), string_to_sign.encode(), hashlib.sha1).digest()
    ).decode()
    return "OSS " + ak + ":" + sig, date


def do_request(verb, path, body=None, extra_headers=None, content_type=""):
    host = endpoint.split("//", 1)[1] if "//" in endpoint else endpoint
    url = "https://" + bucket + "." + host + path
    headers = dict(extra_headers or {})
    auth, date = sign(verb, "/" + bucket + path, headers, content_type=content_type)
    headers["Authorization"] = auth
    headers["Date"] = date
    if content_type:
        headers["Content-Type"] = content_type
    data = body.encode() if isinstance(body, str) else body
    req = urllib.request.Request(url, data=data, method=verb)
    for k, v in headers.items():
        req.add_header(k, v)
    try:
        with urllib.request.urlopen(req, timeout=20) as resp:
            return resp.status, resp.read().decode(errors="replace")
    except urllib.error.HTTPError as e:
        return e.code, e.read().decode(errors="replace")


mode = sys.argv[1] if len(sys.argv) > 1 else "acl"

if mode == "acl":
    st, _ = do_request("PUT", "/?acl", extra_headers={"x-oss-acl": "public-read"})
    print("ACL -> public-read :", st)
elif mode == "test":
    st, _ = do_request("PUT", "/deploy-test/hello.txt", body="hello from oss", content_type="text/plain")
    print("put test obj :", st)
    st, _ = do_request("DELETE", "/deploy-test/hello.txt")
    print("cleaned test obj :", st)
elif mode == "delete":
    key = sys.argv[2]
    st, _ = do_request("DELETE", "/" + key)
    print("deleted", key, ":", st)
else:
    print("unknown mode", mode)
    sys.exit(1)
