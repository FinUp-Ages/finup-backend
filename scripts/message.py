"""Cria um usuario de teste no Cognito (ou loga num existente) e devolve o access token.

Uso, a partir da raiz do repositorio, num terminal seu (o script pede senha e codigo do e-mail):
    python scripts/cognito_token.py

Le COGNITO_USER_POOL_ID e COGNITO_CLIENT_ID do .env. Com a API rodando em localhost, tambem
cadastra o usuario no FinUp (POST /api/v1/users). O token vai para a area de transferencia e para
.finup-token.txt (ignorado pelo git), pronto para colar no Authorize do Swagger.
"""
import getpass
import json
import pathlib
import re
import ssl
import subprocess
import sys
import urllib.error
import urllib.request

REPO = pathlib.Path(__file__).resolve().parent.parent
TOKEN_FILE = REPO / ".finup-token.txt"


def ler_env():
    env_file = REPO / ".env"
    if not env_file.exists():
        sys.exit(f"Nao encontrei {env_file}. Copie o .env.example e preencha as variaveis do Cognito.")
    env = {}
    for line in env_file.read_text(encoding="utf-8").splitlines():
        if "=" in line and not line.lstrip().startswith("#"):
            key, value = line.split("=", 1)
            env[key.strip()] = value.strip()
    return env


ENV = ler_env()
POOL_ID = ENV.get("COGNITO_USER_POOL_ID", "")
CLIENT_ID = ENV.get("COGNITO_CLIENT_ID", "")
if not POOL_ID or not CLIENT_ID:
    sys.exit("COGNITO_USER_POOL_ID e COGNITO_CLIENT_ID precisam estar preenchidos no .env.")
COGNITO = f"https://cognito-idp.{POOL_ID.split('_')[0]}.amazonaws.com/"
API = f"http://localhost:{ENV.get('SERVER_PORT') or '8080'}/api/v1"

# O Python 3.13+ liga VERIFY_X509_STRICT por padrao, e o certificado de ferramentas que
# interceptam TLS (rede da PUCRS, antivirus) costuma nao ter Authority Key Identifier. Desliga so
# essa checagem extra: cadeia, validade e hostname continuam sendo verificados.
TLS = ssl.create_default_context()
TLS.verify_flags &= ~ssl.VERIFY_X509_STRICT


def cognito(action, payload):
    req = urllib.request.Request(
        COGNITO,
        data=json.dumps(payload).encode(),
        headers={
            "Content-Type": "application/x-amz-json-1.1",
            "X-Amz-Target": f"AWSCognitoIdentityProviderService.{action}",
        },
    )
    try:
        with urllib.request.urlopen(req, context=TLS) as resp:
            return json.loads(resp.read() or b"{}")
    except urllib.error.HTTPError as e:
        body = json.loads(e.read() or b"{}")
        return {"_erro": body.get("__type", e.code), "_mensagem": body.get("message", "")}


def api(method, path, token):
    req = urllib.request.Request(
        API + path, method=method, headers={"Authorization": f"Bearer {token}"}
    )
    try:
        with urllib.request.urlopen(req) as resp:
            return resp.status, resp.read().decode()
    except urllib.error.HTTPError as e:
        return e.code, e.read().decode()


def perguntar(texto, obrigatorio=True):
    while True:
        valor = input(texto).strip()
        if valor or not obrigatorio:
            return valor


def confirmar_conta(usuario):
    while True:
        codigo = perguntar("   Codigo enviado por e-mail (ou 'reenviar'): ")
        if codigo.lower() == "reenviar":
            r = cognito("ResendConfirmationCode", {"ClientId": CLIENT_ID, "Username": usuario})
            print(f"   {r.get('_mensagem') or 'codigo reenviado'}")
            continue
        r = cognito(
            "ConfirmSignUp",
            {"ClientId": CLIENT_ID, "Username": usuario, "ConfirmationCode": codigo},
        )
        if "_erro" not in r:
            print("   conta confirmada")
            return
        print(f"   confirmacao falhou: {r['_erro']} - {r['_mensagem']}")


def criar_usuario():
    email = perguntar("E-mail (precisa ser real, o codigo de confirmacao chega nele): ").lower()
    # O pool usa nome de usuario + e-mail como alias: o Username nao pode ter formato de e-mail.
    usuario = perguntar("Nome de usuario no Cognito (sem @, ex.: marco-teste): ")
    nome = perguntar("Nome: ")
    nascimento = perguntar("Data de nascimento (AAAA-MM-DD): ")
    while True:
        senha = getpass.getpass("Senha (min. 8, com maiuscula, minuscula, numero e simbolo): ")
        if senha == getpass.getpass("Repita a senha: "):
            break
        print("   as senhas nao conferem")

    print("\n1) Cadastro no Cognito...")
    atributos = {"email": email, "name": nome, "birthdate": nascimento}
    while True:
        r = cognito(
            "SignUp",
            {
                "ClientId": CLIENT_ID,
                "Username": usuario,
                "Password": senha,
                "UserAttributes": [{"Name": k, "Value": v} for k, v in atributos.items()],
            },
        )
        # Atributo obrigatorio no pool que ainda nao foi informado: pergunta e tenta de novo.
        faltando = re.search(r"aws:cognito:system\.(\w+): The attribute", r.get("_mensagem", ""))
        if r.get("_erro") == "InvalidParameterException" and faltando:
            attr = faltando.group(1)
            atributos[attr] = perguntar(f"   O pool exige o atributo '{attr}': ")
            continue
        if r.get("_erro") == "InvalidPasswordException":
            print(f"   senha recusada: {r['_mensagem']}")
            senha = getpass.getpass("   Nova senha: ")
            continue
        break

    if r.get("_erro") == "UsernameExistsException":
        print("   usuario ja existe no Cognito, seguindo para o login")
    elif "_erro" in r:
        sys.exit(f"   falhou: {r['_erro']} - {r['_mensagem']}")
    else:
        print(f"   criado (sub={r.get('UserSub')})")
        if not r.get("UserConfirmed"):
            confirmar_conta(usuario)
    return usuario, senha


def login(usuario, senha):
    print("\n2) Login no Cognito...")
    while True:
        r = cognito(
            "InitiateAuth",
            {
                "AuthFlow": "USER_AUTH",
                "ClientId": CLIENT_ID,
                "AuthParameters": {
                    "USERNAME": usuario,
                    "PREFERRED_CHALLENGE": "PASSWORD",
                    "PASSWORD": senha,
                },
            },
        )
        if r.get("_erro") == "UserNotConfirmedException":
            print("   a conta ainda nao foi confirmada")
            confirmar_conta(usuario)
            continue
        break
    if "AuthenticationResult" not in r:
        sys.exit(f"   login falhou: {r.get('_erro')} - {r.get('_mensagem')}")
    print("   access token obtido (vale 1 hora)")
    return r["AuthenticationResult"]["AccessToken"]


def cadastrar_na_api(token):
    print(f"\n3) API do FinUp ({API})")
    for method, path in [("POST", "/users"), ("GET", "/users/me")]:
        try:
            status, body = api(method, path, token)
        except urllib.error.URLError:
            print("   API fora do ar: suba o backend e chame POST /api/v1/users pelo Swagger.")
            return
        # 409 no POST so quer dizer que o usuario ja estava cadastrado no FinUp.
        print(f"   {method} {path} -> {status}\n   {body}\n")


def main():
    print(f"User Pool: {POOL_ID}\n")
    print("1) Criar um usuario novo e pegar o token")
    print("2) Ja tenho usuario, so pegar o token")
    opcao = perguntar("Opcao [1/2]: ")
    print()
    if opcao == "1":
        usuario, senha = criar_usuario()
    else:
        usuario = perguntar("Nome de usuario (ou e-mail, se ja confirmado): ")
        senha = getpass.getpass("Senha: ")

    token = login(usuario, senha)
    cadastrar_na_api(token)

    TOKEN_FILE.write_text(token, encoding="utf-8")
    try:
        subprocess.run("clip", input=token.encode("utf-16-le"), check=True, shell=True)
        print("Token copiado para a area de transferencia.")
    except (OSError, subprocess.CalledProcessError):
        pass
    print(f"Token salvo em {TOKEN_FILE.name}. No Swagger: Authorize -> bearerAuth -> cole o token.")


if __name__ == "__main__":
    try:
        main()
    except KeyboardInterrupt:
        sys.exit("\ncancelado")
