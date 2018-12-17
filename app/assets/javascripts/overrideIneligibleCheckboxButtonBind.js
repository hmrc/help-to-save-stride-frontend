/*
    For the ineligible page, the call handler can override the ineligibility response, but to
    do so, they must check a box to confirm there is evidence to support their eligibility

    This binds the enabled/disabled status of the confirmation button with the confirmation
    checkbox
*/

;(function bindIneligibleContinueButtonWithCheckbox() {
  var checkbox = $('.button-disable-binding');
  var button = $('.checkbox-disable-binding');

  checkbox.change(function(e) {
    var isChecked = e.currentTarget.checked;
    button.prop('disabled', !isChecked);
    isChecked ? button.removeAttr('aria-disabled') : button.attr('aria-disabled', 'true')
  });

  /* 
    The checked attribute must be set on the input when the page loads if:
    - The page has errors
    - The form has data
  */
  if (checkbox.hasClass('checked')) {
    checkbox.prop('checked', true)
    
    button
      .removeAttr('aria-disabled')
      .prop('disabled', false)
  }

})();
