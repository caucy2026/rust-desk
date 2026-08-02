import 'package:auto_size_text/auto_size_text.dart';
import 'package:flutter/material.dart';
import 'package:get/get.dart';

import '../../common.dart';

Widget getConnectionPageTitle(BuildContext context, bool isWeb) {
  final appVersion = version.trim();
  final titleColor = Theme.of(context).textTheme.titleLarge?.color;
  return Row(
    children: [
      Expanded(
          child: Row(
        children: [
          Flexible(
            child: AutoSizeText(
              translate('Control Remote Desktop'),
              maxLines: 1,
              minFontSize: 12,
              style: Theme.of(context)
                  .textTheme
                  .titleLarge
                  ?.merge(const TextStyle(height: 1)),
            ),
          ),
          if (appVersion.isNotEmpty)
            Text(
              'v$appVersion',
              maxLines: 1,
              style: TextStyle(
                height: 1,
                fontSize: 11,
                fontWeight: FontWeight.w400,
                color: titleColor?.withOpacity(0.55),
              ),
            ).marginOnly(left: 6, right: 5, top: 3),
          Tooltip(
            waitDuration: Duration(milliseconds: 300),
            message: translate(isWeb ? "web_id_input_tip" : "id_input_tip"),
            child: Icon(
              Icons.help_outline_outlined,
              size: 16,
              color: titleColor?.withOpacity(0.5),
            ),
          ),
        ],
      )),
    ],
  );
}
