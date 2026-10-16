"""Copy a MinIO bucket to OSS, preserving object keys. Default: list only.
Existing target objects are verified by SHA-256 and never blindly overwritten.
"""
from pathlib import Path
import argparse
import hashlib
from migrate_mysql_to_postgres import load_env

def checksum(stream):
    digest = hashlib.sha256()
    while chunk := stream.read(1024*1024): digest.update(chunk)
    return digest.digest()

def main():
    import boto3
    import oss2
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--env-file",default=str(Path(__file__).resolve().parents[1]/".env"))
    parser.add_argument("--execute",action="store_true")
    args = parser.parse_args()
    env = load_env(args.env_file)
    required = ["MINIO_ENDPOINT","MINIO_ACCESS_KEY","MINIO_SECRET_KEY","MINIO_BUCKET",
                "OSS_ENDPOINT","OSS_ACCESS_KEY_ID","OSS_ACCESS_KEY_SECRET","OSS_BUCKET"]
    if any(not env.get(key) for key in required): raise SystemExit("Configure MinIO source and OSS target credentials in the env file.")
    source=boto3.client("s3",endpoint_url=env["MINIO_ENDPOINT"],
        aws_access_key_id=env["MINIO_ACCESS_KEY"],aws_secret_access_key=env["MINIO_SECRET_KEY"])
    auth = oss2.StsAuth(env["OSS_ACCESS_KEY_ID"],env["OSS_ACCESS_KEY_SECRET"],env["OSS_SECURITY_TOKEN"]) if env.get("OSS_SECURITY_TOKEN") else oss2.Auth(env["OSS_ACCESS_KEY_ID"],env["OSS_ACCESS_KEY_SECRET"])
    target=oss2.Bucket(auth,env["OSS_ENDPOINT"],env["OSS_BUCKET"])
    total=0
    for page in source.get_paginator("list_objects_v2").paginate(Bucket=env["MINIO_BUCKET"]):
        for item in page.get("Contents",[]):
            key=item["Key"]; total+=1
            if not args.execute: continue
            if not target.object_exists(key):
                obj=source.get_object(Bucket=env["MINIO_BUCKET"],Key=key)
                with obj["Body"] as stream:
                    target.put_object(key,stream,headers={"Content-Type":obj.get("ContentType","application/octet-stream")})
            original=source.get_object(Bucket=env["MINIO_BUCKET"],Key=key)
            copied=target.get_object(key)
            try:
                with original["Body"] as stream: expected=checksum(stream)
                if checksum(copied)!=expected: raise RuntimeError("Object checksum mismatch; stop cutover")
            finally:
                copied.close()
    print(f"{'Copied and SHA-256 verified' if args.execute else 'Preflight listed'} {total} objects. Source unchanged.")

if __name__ == "__main__": main()
