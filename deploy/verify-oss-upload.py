#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
一键验证：全站图片上传已接通阿里云 OSS（公共读直链）。

验证项：
  1. 管理员上传「商品封面」(type=product)  -> 返回 OSS 公网直链 -> 公开 GET 200
  2. 管理员上传「分类图标」(type=category)  -> 返回 OSS 公网直链 -> 公开 GET 200
  3. 登录用户上传「用户头像」(type=avatar)   -> 返回 OSS 公网直链 -> 公开 GET 200
  4. 登录用户上传「评价晒图」(type=review)   -> 返回 OSS 公网直链 -> 公开 GET 200

清理：若环境变量提供 OSS 凭证（OSS_ENDPOINT/OSS_BUCKET/OSS_ACCESS_KEY_ID/OSS_ACCESS_KEY_SECRET），
      脚本会删除本次创建的 OSS 对象；否则仅打印提示，请手动清理。

用法：
  python deploy/verify-oss-upload.py
  VERIFY_HOST=http://localhost:8080/api VERIFY_ADMIN=admin VERIFY_ADMIN_PW=123456 python deploy/verify-oss-upload.py
依赖：仅 Python 标准库（urllib / hmac / hashlib），无需安装第三方包。
"""
import base64
import datetime
import hashlib
import hmac
import json
import os
import sys
import uuid
import urllib.error
import urllib.request

HOST = os.getenv("VERIFY_HOST", "http://localhost:8080/api")
ADMIN = os.getenv("VERIFY_ADMIN", "admin")
ADMIN_PW = os.getenv("VERIFY_ADMIN_PW", "123456")

# 1x1 透明 PNG
PNG = base64.b64decode(
    "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+M8AAAMCAQDJ/3pUAAAAAElFTkSuQmCC"
)


# ----------------------------- HTTP 基础封装 -----------------------------
def api(method, path, token=None, json_body=None, fields=None, files=None):
    url = HOST + path
    data = None
    headers = {}
    if token:
        headers["Authorization"] = "Bearer " + token
    if json_body is not None:
        data = json.dumps(json_body).encode("utf-8")
        headers["Content-Type"] = "application/json"
    if fields or files:
        boundary = "----bnd" + uuid.uuid4().hex
        parts = []
        for k, v in (fields or {}).items():
            parts.append(("--" + boundary).encode())
            parts.append(('Content-Disposition: form-data; name="%s"' % k).encode())
            parts.append(b"")
            parts.append(str(v).encode("utf-8"))
        for (name, fname, content, ctype) in (files or []):
            parts.append(("--" + boundary).encode())
            parts.append(
                ('Content-Disposition: form-data; name="%s"; filename="%s"' % (name, fname)).encode()
            )
            parts.append(("Content-Type: %s" % ctype).encode())
            parts.append(b"")
            parts.append(content)
        parts.append(("--" + boundary + "--").encode())
        parts.append(b"")
        data = b"\r\n".join(parts)
        headers["Content-Type"] = "multipart/form-data; boundary=" + boundary
    req = urllib.request.Request(url, data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(req, timeout=20) as r:
            return r.status, json.loads(r.read().decode("utf-8"))
    except urllib.error.HTTPError as e:
        try:
            return e.code, json.loads(e.read().decode("utf-8"))
        except Exception:
            return e.code, {}
    except Exception as e:  # 网络层错误
        return 0, {"error": str(e)}


def login(user, pw):
    st, body = api("POST", "/auth/login", json_body={"username": user, "password": pw})
    if st != 200 or not (body.get("data") or {}).get("token"):
        raise SystemExit("登录失败 HTTP=%s body=%s" % (st, body))
    return body["data"]["token"]


def public_get(url):
    try:
        with urllib.request.urlopen(url, timeout=20) as r:
            return r.status
    except urllib.error.HTTPError as e:
        return e.code
    except Exception:
        return 0


# ----------------------------- OSS 清理（stdlib 签名） -----------------------------
def oss_delete(key):
    ep = os.getenv("OSS_ENDPOINT")
    bk = os.getenv("OSS_BUCKET")
    ak = os.getenv("OSS_ACCESS_KEY_ID")
    sk = os.getenv("OSS_ACCESS_KEY_SECRET")
    if not (ep and bk and ak and sk):
        return None  # 无凭证，跳过
    if not ep.startswith("http"):
        ep = "https://" + ep
    host = ep.split("//", 1)[1]
    date = datetime.datetime.now(datetime.timezone.utc).strftime("%a, %d %b %Y %H:%M:%S GMT")
    string = "DELETE\n\n\n%s\n/%s/%s" % (date, bk, key)
    sig = base64.b64encode(
        hmac.new(sk.encode("utf-8"), string.encode("utf-8"), hashlib.sha1).digest()
    ).decode()
    auth = "OSS %s:%s" % (ak, sig)
    url = "https://%s.%s/%s" % (bk, host, key)
    req = urllib.request.Request(url, method="DELETE", headers={"Date": date, "Authorization": auth})
    try:
        with urllib.request.urlopen(req, timeout=20) as r:
            return r.status
    except urllib.error.HTTPError as e:
        return e.code
    except Exception:
        return 0


def key_from_url(base_url, url):
    if not url or not base_url:
        return None
    if not url.startswith(base_url):
        return None
    return url[len(base_url):].lstrip("/")


# ----------------------------- 主流程 -----------------------------
def upload_and_check(token, biz_type):
    st, body = api(
        "POST",
        "/files/upload",
        token=token,
        fields={"type": biz_type},
        files=[("file", "t.png", PNG, "image/png")],
    )
    if st != 200 or not (body.get("data") or {}).get("url"):
        return False, "上传失败 HTTP=%s body=%s" % (st, body)
    url = body["data"]["url"]
    code = public_get(url)
    ok = code == 200
    return ok, "url=%s 公开GET=%s" % (url, code)


def main():
    print("=== 验证后端: %s ===" % HOST)
    token = login(ADMIN, ADMIN_PW)
    print("管理员登录成功 (token 长度 %d)" % len(token))

    cases = [
        ("商品封面 product", "product"),
        ("分类图标 category", "category"),
        ("用户头像 avatar", "avatar"),
        ("评价晒图 review", "review"),
    ]

    base_url = "https://super-zzm.oss-cn-beijing.aliyuncs.com"
    all_ok = True
    created = []
    for label, biz in cases:
        ok, detail = upload_and_check(token, biz)
        all_ok = all_ok and ok
        status = "PASS" if ok else "FAIL"
        print("[%s] %-18s %s" % (status, label, detail))
        if ok and "url=" in detail:
            url = detail.split("url=", 1)[1].split(" ")[0]
            k = key_from_url(base_url, url)
            if k:
                created.append(k)

    # 清理 OSS 测试对象
    if created:
        have_cred = all(
            os.getenv(x)
            for x in ("OSS_ENDPOINT", "OSS_BUCKET", "OSS_ACCESS_KEY_ID", "OSS_ACCESS_KEY_SECRET")
        )
        if have_cred:
            print("=== 清理 OSS 测试对象 ===")
            for k in created:
                c = oss_delete(k)
                print("  删除 %s -> %s" % (k, c))
        else:
            print("=== 未提供 OSS 凭证，跳过自动清理，请手动删除以下对象 ===")
            for k in created:
                print("  " + base_url + "/" + k)

    print("\n=== 结论: %s ===" % ("全部通过 ✅" if all_ok else "存在失败 ❌"))
    sys.exit(0 if all_ok else 1)


if __name__ == "__main__":
    main()
