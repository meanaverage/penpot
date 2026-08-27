#!/usr/bin/env bash

is_truthy() {
  local value="${1,,}"
  [[ "$value" == "true" || "$value" == "t" || "$value" == "1" ]]
}

is_falsy() {
  local value="${1,,}"
  [[ "$value" == "false" || "$value" == "f" || "$value" == "0" ]]
}


#########################################
## Air Gapped config
#########################################

if [[ $PENPOT_FLAGS == *"enable-air-gapped-conf"* ]]; then
    rm /etc/nginx/overrides/location.d/external-locations.conf;
    export PENPOT_FLAGS="$PENPOT_FLAGS disable-google-fonts-provider disable-dashboard-templates-section"
fi

#########################################
## App Frontend config
#########################################

update_flags() {
  if [ -n "$PENPOT_FLAGS" ]; then
    echo "$(sed \
      -e "s|^//var penpotFlags = .*;|var penpotFlags = \"$PENPOT_FLAGS\";|g" \
      "$1")" > "$1"
  fi

  if [ -n "$PENPOT_PUBLIC_URI" ]; then
      echo "var penpotPublicURI = \"$PENPOT_PUBLIC_URI\";" >> "$1";
  fi
}

update_oidc_name() {
  if [ -n "$PENPOT_OIDC_NAME" ]; then
    echo "$(sed \
      -e "s|^//var penpotOIDCName = .*;|var penpotOIDCName = \"$PENPOT_OIDC_NAME\";|g" \
      "$1")" > "$1"
  fi
}

update_sayhi_studio_uri() {
  if [ -n "$PENPOT_SAYHI_STUDIO_URI" ]; then
    if [[ "$PENPOT_SAYHI_STUDIO_URI" != http://* && "$PENPOT_SAYHI_STUDIO_URI" != https://* ]]; then
      echo "PENPOT_SAYHI_STUDIO_URI must be an HTTP(S) URL" >&2
      exit 1
    fi

    if [[ "$PENPOT_SAYHI_STUDIO_URI" == *$'\n'* || "$PENPOT_SAYHI_STUDIO_URI" == *$'\r'* ]]; then
      echo "PENPOT_SAYHI_STUDIO_URI must not contain line breaks" >&2
      exit 1
    fi

    local escaped_uri="${PENPOT_SAYHI_STUDIO_URI//\\/\\\\}"
    escaped_uri="${escaped_uri//\"/\\\"}"
    printf 'var penpotSayHiStudioURI = "%s";\n' "$escaped_uri" >> "$1"
  fi
}

update_sayhi_motion_studio_mode() {
  if [ -n "$PENPOT_SAYHI_MOTION_STUDIO_MODE" ]; then
    if [[ "$PENPOT_SAYHI_MOTION_STUDIO_MODE" != "legacy" && "$PENPOT_SAYHI_MOTION_STUDIO_MODE" != "native-v1" && "$PENPOT_SAYHI_MOTION_STUDIO_MODE" != "native-v2" ]]; then
      echo "PENPOT_SAYHI_MOTION_STUDIO_MODE must be legacy, native-v1, or native-v2" >&2
      exit 1
    fi

    printf 'var penpotSayHiMotionStudioMode = "%s";\n' "$PENPOT_SAYHI_MOTION_STUDIO_MODE" >> "$1"
  fi
}

update_sayhi_motion_studio_uri() {
  if [ -n "$PENPOT_SAYHI_MOTION_STUDIO_URI" ]; then
    if [[ "$PENPOT_SAYHI_MOTION_STUDIO_URI" != http://* && "$PENPOT_SAYHI_MOTION_STUDIO_URI" != https://* ]]; then
      echo "PENPOT_SAYHI_MOTION_STUDIO_URI must be an HTTP(S) URL" >&2
      exit 1
    fi

    if [[ "$PENPOT_SAYHI_MOTION_STUDIO_URI" == *$'\n'* || "$PENPOT_SAYHI_MOTION_STUDIO_URI" == *$'\r'* ]]; then
      echo "PENPOT_SAYHI_MOTION_STUDIO_URI must not contain line breaks" >&2
      exit 1
    fi

    local escaped_uri="${PENPOT_SAYHI_MOTION_STUDIO_URI//\\/\\\\}"
    escaped_uri="${escaped_uri//\"/\\\"}"
    printf 'var penpotSayHiMotionStudioURI = "%s";\n' "$escaped_uri" >> "$1"
  fi
}

update_sayhi_motion_preview_surface() {
  if [ -n "$PENPOT_SAYHI_MOTION_PREVIEW_SURFACE" ]; then
    if [[ "$PENPOT_SAYHI_MOTION_PREVIEW_SURFACE" != "canvas" && "$PENPOT_SAYHI_MOTION_PREVIEW_SURFACE" != "focused" ]]; then
      echo "PENPOT_SAYHI_MOTION_PREVIEW_SURFACE must be canvas or focused" >&2
      exit 1
    fi

    printf 'var penpotSayHiMotionPreviewSurface = "%s";\n' "$PENPOT_SAYHI_MOTION_PREVIEW_SURFACE" >> "$1"
  fi
}

update_sayhi_web_materializer_mode() {
  if [ -n "$PENPOT_SAYHI_WEB_MATERIALIZER_MODE" ]; then
    if [[ "$PENPOT_SAYHI_WEB_MATERIALIZER_MODE" != "projection-v1" && "$PENPOT_SAYHI_WEB_MATERIALIZER_MODE" != "portable-v2" && "$PENPOT_SAYHI_WEB_MATERIALIZER_MODE" != "shadow" ]]; then
      echo "PENPOT_SAYHI_WEB_MATERIALIZER_MODE must be projection-v1, portable-v2, or shadow" >&2
      exit 1
    fi

    printf 'var penpotSayHiWebMaterializerMode = "%s";\n' "$PENPOT_SAYHI_WEB_MATERIALIZER_MODE" >> "$1"
  fi
}

update_flags /var/www/app/js/config.js
update_oidc_name /var/www/app/js/config.js
update_sayhi_studio_uri /var/www/app/js/config.js
update_sayhi_motion_studio_mode /var/www/app/js/config.js
update_sayhi_motion_studio_uri /var/www/app/js/config.js
update_sayhi_motion_preview_surface /var/www/app/js/config.js
update_sayhi_web_materializer_mode /var/www/app/js/config.js

#########################################
## Nginx Config
#########################################

export PENPOT_BACKEND_URI=${PENPOT_BACKEND_URI:-http://penpot-backend:6060}
export PENPOT_EXPORTER_URI=${PENPOT_EXPORTER_URI:-http://penpot-exporter:6061}
export PENPOT_NITRATE_URI=${PENPOT_NITRATE_URI:-http://penpot-nitrate:3000}
export PENPOT_HTTP_SERVER_MAX_BODY_SIZE=${PENPOT_HTTP_SERVER_MAX_BODY_SIZE:-367001600} # Default to 350MiB
export PENPOT_IPV6_LISTEN_DIRECTIVE=${PENPOT_IPV6_LISTEN_DIRECTIVE:-"listen [::]:8080 default_server reuseport backlog=16384;"}
if is_truthy "${PENPOT_DISABLE_IPV6_LISTEN:-}"; then
  export PENPOT_IPV6_LISTEN_DIRECTIVE=""
fi
envsubst "\$PENPOT_BACKEND_URI,\$PENPOT_EXPORTER_URI,\$PENPOT_NITRATE_URI,\$PENPOT_HTTP_SERVER_MAX_BODY_SIZE,\$PENPOT_IPV6_LISTEN_DIRECTIVE" \
        < /tmp/nginx.conf.template > /etc/nginx/nginx.conf

if [[ $PENPOT_FLAGS == *"enable-mcp"* ]]; then
    export PENPOT_MCP_URI=${PENPOT_MCP_URI:-http://penpot-mcp:4401}
    export PENPOT_MCP_URI_WS=${PENPOT_MCP_URI_WS:-http://penpot-mcp:4402}

    envsubst "\$PENPOT_MCP_URI,\$PENPOT_MCP_URI_WS" \
             < /tmp/nginx-mcp-locations.conf.template > /etc/nginx/overrides/server.d/mcp-locations.conf
else
    rm -f /etc/nginx/overrides/server.d/mcp-locations.conf
fi

PENPOT_DEFAULT_INTERNAL_RESOLVER="$(awk 'BEGIN{ORS=" "} $1=="nameserver" { sub(/%.*$/,"",$2); print ($2 ~ ":")? "["$2"]": $2}' /etc/resolv.conf)"
export PENPOT_INTERNAL_RESOLVER=${PENPOT_INTERNAL_RESOLVER:-$PENPOT_DEFAULT_INTERNAL_RESOLVER}
envsubst "\$PENPOT_INTERNAL_RESOLVER" \
         < /tmp/resolvers.conf.template > /etc/nginx/overrides/http.d/resolvers.conf

exec "$@";
